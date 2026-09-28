package org.example.am.internal.web.config;

import java.util.List;

import org.example.am.internal.service.AmsUserDetailsService;
import org.example.am.internal.web.security.WebSealPreAuthenticatedAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Registers the authentication provider globally, so that method level security in the service
 * layer resolves against the same authorities as the URL rules do.
 *
 * <p>{@code @EnableMethodSecurity} belongs here rather than on {@link ServletConfig}. This
 * configuration is imported by {@code RootConfig}, so it lives in the root context; the annotation
 * on the {@code DispatcherServlet} context could not reach the beans it governs, and the
 * application would fail to start.</p>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, jsr250Enabled = true, securedEnabled = true)
public class GlobalSecurityConfig {

    private final AmsUserDetailsService amsUserDetailsService;

    public GlobalSecurityConfig(final AmsUserDetailsService amsUserDetailsService) {
        this.amsUserDetailsService = amsUserDetailsService;
    }

    @Bean
    public WebSealPreAuthenticatedAuthenticationProvider webSealAuthenticationProvider() {
        return new WebSealPreAuthenticatedAuthenticationProvider(amsUserDetailsService);
    }

    /**
     * The only provider: there is no form login and no in-memory fallback, so a request that
     * does not arrive pre-authenticated cannot authenticate at all. This ProviderManager
     * replaces the former {@code configure(AuthenticationManagerBuilder)} override, which
     * registered exactly this single provider and nothing else.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(webSealAuthenticationProvider()));
    }
}
