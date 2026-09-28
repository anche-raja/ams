package org.example.am.internal.web.config;

import org.example.am.internal.web.interceptors.SpecialCharacterInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

/**
 * The {@code DispatcherServlet} context, mounted at {@code /ams/*}.
 *
 * <p>Spring MVC serves exactly two pages here - the two global error views. Everything functional
 * is a Struts action. The servlet exists at all because the error pages need to be reachable by a
 * plain URL that is not routed through the Struts filter, and because the session-expiry redirect
 * has to land somewhere that does not itself require a session.</p>
 */
@Configuration
@EnableWebMvc
@ComponentScan("org.example.am.internal.web.controller")
@Import(WebSecurityConfig.class)
public class ServletConfig implements WebMvcConfigurer {

    /*
     * @EnableGlobalMethodSecurity is deliberately NOT here. It has to sit alongside the
     * GlobalMethodSecurityConfiguration subclass it configures, which is GlobalSecurityConfig in
     * the root context. Adding it here either fails to reach that subclass - the application then
     * refuses to start - or builds a second, competing method security setup in this context.
     */

    private static final int STATIC_RESOURCE_CACHE_SECONDS = 600;

    @Bean
    public InternalResourceViewResolver viewResolver() {
        final InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setViewClass(JstlView.class);
        resolver.setPrefix("/WEB-INF/");
        resolver.setSuffix(".jsp");
        return resolver;
    }

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        // Trailing '**' is the only legal position for it under PathPattern, so this mapping is
        // still valid as written.
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("/resources/")
                .setCachePeriod(Integer.valueOf(STATIC_RESOURCE_CACHE_SECONDS));
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        // The Struts side has its own equivalent; a request must be checked whichever framework
        // ends up serving it.
        registry.addInterceptor(new SpecialCharacterInterceptor());
    }

    /**
     * Extension based content negotiation.
     *
     * <p>The URLs this application has always exposed end in {@code .action}, and the AJAX
     * endpoints are distinguished by suffix rather than by an {@code Accept} header, which is what
     * the vendored Dojo build sends.</p>
     *
     * <p>TODO(migration): {@code favorPathExtension(true)} and {@code useRegisteredExtensionsOnly}
     * were REMOVED in Spring 6 - path-extension content negotiation no longer exists. The
     * suffix-driven ({@code .action}, {@code json}/{@code html} by extension) negotiation this
     * method relied on cannot be reconstructed mechanically. The original configuration is
     * preserved below in comment form only; the live configuration falls back to Accept-header
     * negotiation with a TEXT_HTML default so the two error views still resolve. Reproducing the
     * old suffix behaviour requires an explicit design decision (see manual_flags).</p>
     */
    @Override
    public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
        // Original (Spring 5) configuration, no longer compilable in Spring 6:
        //   configurer.favorPathExtension(true)
        //             .ignoreAcceptHeader(false)
        //             .useRegisteredExtensionsOnly(false)
        //             .defaultContentType(MediaType.TEXT_HTML)
        //             .mediaType("html", MediaType.TEXT_HTML)
        //             .mediaType("json", MediaType.APPLICATION_JSON);
        configurer.ignoreAcceptHeader(false)
                .defaultContentType(org.springframework.http.MediaType.TEXT_HTML)
                .mediaType("html", org.springframework.http.MediaType.TEXT_HTML)
                .mediaType("json", org.springframework.http.MediaType.APPLICATION_JSON);
    }

    /**
     * Path matching configuration.
     *
     * <p>TODO(migration): the previous implementation called {@code configurer.setPatternParser(null)}
     * to force the legacy {@code AntPathMatcher} and keep suffix pattern matching alive. In Spring 6
     * {@code PathPatternParser} is the default and the escape hatch back to {@code AntPathMatcher}
     * no longer restores suffix pattern matching - suffix pattern matching was removed outright.
     * Clearing the parser here therefore no longer achieves what the comment described, so the call
     * has been removed rather than left as a misleading no-op. Trailing-slash matching is also off
     * by default in 6 (see manual_flags). This override is retained empty as a marker; the mappings
     * that depended on {@code .action} suffix behaviour must be reviewed at their declaration
     * sites.</p>
     */
    @Override
    public void configurePathMatch(final PathMatchConfigurer configurer) {
        // Intentionally empty. Original (Spring 5) call, invalid intent under Spring 6:
        //   configurer.setPatternParser(null);
    }
}
