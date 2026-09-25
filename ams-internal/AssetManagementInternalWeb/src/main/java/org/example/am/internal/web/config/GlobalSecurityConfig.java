package org.example.am.internal.web.config;

import org.example.am.internal.service.AmsUserDetailsService;
import org.example.am.internal.web.security.WebSealPreAuthenticatedAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Registers the authentication provider globally, so that method level security in the service
 * layer resolves against the same authorities as the URL rules do.
 *
 * <p>{@code @EnableMethodSecurity} belongs here rather than on {@link ServletConfig}. The
 * annotation has to be visible in the same context that drives method security. This configuration
 * is imported by {@code RootConfig}, so it lives in the root context; the annotation on the
 * {@code DispatcherServlet} context could not reach it.</p>
 *
 * <p>Migrated from {@code GlobalMethodSecurityConfiguration}: the former
 * {@code configure(AuthenticationManagerBuilder)} override, which registered a single
 * {@link WebSealPreAuthenticatedAuthenticationProvider} as the only provider, is now expressed as
 * an explicit {@link AuthenticationManager} bean backed by a {@link ProviderManager} wrapping that
 * one provider. There is still no form login and no in-memory fallback, so a request that does not
 * arrive pre-authenticated cannot authenticate at all.</p>
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

    @Bean
    public AuthenticationManager authenticationManager() {
        // The only provider: there is no form login and no in-memory fallback, so a request that
        // does not arrive pre-authenticated cannot authenticate at all.
        return new ProviderManager(webSealAuthenticationProvider());
    }
}
