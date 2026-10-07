package org.example.am.internal.web.config;

import org.example.am.internal.service.AmsUserDetailsService;
import org.example.am.internal.web.security.WebSealPreAuthenticatedAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Registers the authentication provider globally, so that method level security in the service
 * layer resolves against the same authorities as the URL rules do.
 *
 * <p>{@code @EnableMethodSecurity} belongs here rather than on {@link ServletConfig}: this
 * configuration is imported by {@code RootConfig}, so it lives in the root context where the
 * service-layer beans it guards are defined. The annotation on the {@code DispatcherServlet}
 * context could not reach them.</p>
 *
 * <p>In Spring Security 6 {@code GlobalMethodSecurityConfiguration} and the
 * {@code configure(AuthenticationManagerBuilder)} hook are gone; the single authentication
 * provider is exposed as a bean below and assembled into the {@code AuthenticationManager} by
 * {@link WebSecurityConfig}. The effective behaviour is unchanged: there is still exactly one
 * provider, and a request that does not arrive pre-authenticated cannot authenticate at all.</p>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, jsr250Enabled = true, securedEnabled = true)
public class GlobalSecurityConfig {

    @Autowired
    private AmsUserDetailsService amsUserDetailsService;

    @Bean
    public WebSealPreAuthenticatedAuthenticationProvider webSealAuthenticationProvider() {
        return new WebSealPreAuthenticatedAuthenticationProvider(amsUserDetailsService);
    }
}
