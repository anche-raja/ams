package org.example.am.internal.web.config;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.web.security.DevWebSealRequestHeaderAuthenticationFilter;
import org.example.am.internal.web.security.RemoveRolesPrefixPostProcessor;
import org.example.am.internal.web.security.WebSealPreAuthenticatedAuthenticationProvider;
import org.example.am.internal.web.security.WebSealRequestHeaderAuthenticationFilter;
import org.example.am.internal.web.security.csrf.CSRFTokenRequestMatcher;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The filter chain.
 *
 * <p>Three things are worth knowing about this configuration. First, there is no login page: the
 * entry point returns 403 rather than redirecting, because a browser that reaches this application
 * without proxy headers has bypassed the proxy and there is nowhere useful to send it. Second, the
 * pre-authentication filter is chosen by profile, so the developer stub cannot be reached in
 * production. Third, the content security policy allows inline and eval'd script, which the
 * vendored Dojo build requires - tightening it means replacing that toolkit first.</p>
 *
 * <p>Migrated to Spring Security 6: the former configurer-adapter base class is replaced by a
 * {@link SecurityFilterChain} bean and the lambda DSL. The authorization rules, their order, the
 * CSRF matcher, session management, headers and the 403 entry point are preserved verbatim.
 * X-XSS-Protection is kept at {@code 1; mode=block} rather than taking Spring Security 6's new
 * default of {@code 0}, so the response headers match the Struts-side interceptor and the
 * pre-migration behaviour.</p>
 *
 * <p>The permitted paths are matched with explicit {@link AntPathRequestMatcher}s. In Spring
 * Security 6 a bare {@code requestMatchers(String...)} resolves to an MVC matcher and requires a
 * {@code HandlerMappingIntrospector} from a Spring MVC {@code DispatcherServlet} context. This
 * application routes through Struts, not Spring MVC, so there is no such context; ant matchers
 * reproduce exactly the string-pattern matching the pre-migration configuration used.</p>
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private static final Logger LOGGER = LogManager.getLogger(WebSecurityConfig.class);

    /**
     * Reachable without authentication.
     *
     * <p>The two error actions are here so that a rejected request can be shown an explanation
     * rather than a second rejection. The health endpoints are here because the container's monitor
     * polls them with no credentials; they return a fixed string and read nothing.</p>
     */
    private static final String[] PERMITTED_PATHS = {
            "/Unauthorized.action*",
            "/CustomError.action*",
            "/health",
            "/health.action" };

    /**
     * Inline and eval'd script are permitted because the vendored Dojo 1.17 build relies on both.
     * Everything else is same origin only.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "frame-ancestors 'self'; "
            + "form-action 'self'";

    private static final int MAX_CONCURRENT_SESSIONS = 1;

    @Autowired
    private WebSealPreAuthenticatedAuthenticationProvider webSealAuthenticationProvider;

    @Autowired
    private Environment environment;

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .requireCsrfProtectionMatcher(csrfTokenRequestMatcher()))
            .addFilter(preAuthenticatedProcessingFilter())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(permittedRequestMatchers()).permitAll()
                // Everything else needs an authenticated user. Which actions that user may then
                // perform is decided per action by the role checks in BaseAction, because the role
                // model is far too fine grained to express as URL patterns.
                .anyRequest().hasAuthority(SecurityRoleType.ROLE_USER))
            .httpBasic(httpBasic -> httpBasic
                // 403 rather than a redirect: there is no login page to send anyone to.
                .authenticationEntryPoint(new Http403ForbiddenEntryPoint()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(MAX_CONCURRENT_SESSIONS)
                .expiredUrl("/ams/invalidSessionError"))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                // Content-Type-Options and Cache-Control are on by default in Spring Security 6,
                // as they were explicitly enabled before. X-XSS-Protection defaults to "0" in 6;
                // restored to the previous "1; mode=block" so the header is unchanged.
                .xssProtection(xss -> xss
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true))
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                        CONTENT_SECURITY_POLICY)));
        return http.build();
    }

    /**
     * The permitted paths as explicit ant matchers, preserving the exact string patterns and their
     * order. Built here rather than inline so the pattern list stays a single source of truth.
     */
    private static RequestMatcher[] permittedRequestMatchers() {
        final RequestMatcher[] matchers = new RequestMatcher[PERMITTED_PATHS.length];
        for (int i = 0; i < PERMITTED_PATHS.length; i++) {
            matchers[i] = new AntPathRequestMatcher(PERMITTED_PATHS[i]);
        }
        return matchers;
    }

    /**
     * Chooses the pre-authentication filter by profile.
     *
     * <p>The developer stub asserts an identity when no proxy header is present, so it must never
     * be registered anywhere a real user could reach it. Production and QA sit behind the proxy and
     * get the real filter; the local, dev and FIT profiles get the stub.</p>
     */
    @Bean
    public AbstractPreAuthenticatedProcessingFilter preAuthenticatedProcessingFilter()
            throws Exception {
        final AbstractPreAuthenticatedProcessingFilter filter;
        if (isNonProductionProfile()) {
            final DevWebSealRequestHeaderAuthenticationFilter devFilter =
                    new DevWebSealRequestHeaderAuthenticationFilter();
            devFilter.setDeveloperGroups(Arrays.asList("AMS_INTERNAL_OPERATIONS", "AMS_INTERNAL_ADMIN"));
            LOGGER.warn("Registering the developer pre-authentication stub;"
                    + " active profiles are {}", Arrays.toString(environment.getActiveProfiles()));
            filter = devFilter;
        } else {
            filter = new WebSealRequestHeaderAuthenticationFilter();
        }
        filter.setAuthenticationManager(authenticationManager());
        // A request without headers is not an error here; it simply fails the authorisation rules
        // further down the chain and is answered with a 403 by the entry point.
        filter.setContinueFilterChainOnUnsuccessfulAuthentication(true);
        return filter;
    }

    private boolean isNonProductionProfile() {
        for (final String profile : environment.getActiveProfiles()) {
            if (CommonConstants.PROFILE_LOCAL.equalsIgnoreCase(profile)
                    || CommonConstants.PROFILE_DEV.equalsIgnoreCase(profile)
                    || CommonConstants.PROFILE_FIT.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public CSRFTokenRequestMatcher csrfTokenRequestMatcher() {
        return new CSRFTokenRequestMatcher();
    }

    @Bean
    public static RemoveRolesPrefixPostProcessor removeRolesPrefixPostProcessor() {
        return new RemoveRolesPrefixPostProcessor();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(Arrays.asList(
                (org.springframework.security.authentication.AuthenticationProvider)
                        webSealAuthenticationProvider));
    }
}
