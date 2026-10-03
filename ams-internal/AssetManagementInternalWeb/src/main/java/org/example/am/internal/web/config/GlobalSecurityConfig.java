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
 * <p>{@code @EnableMethodSecurity} belongs here rather than on {@link ServletConfig}. This is
 * the class that extends {@link org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration}, and the annotation has to be
 * visible in the same context as that subclass - it is what supplies the subclass with which
 * annotations to honour. This configuration is imported by {@code RootConfig}, so it lives in the
 * root context; the annotation on the {@code DispatcherServlet} context could not reach it, and the
 * application would fail to start with "EnableMethodSecurity is required".</p>
 *
 * <p>In Spring Security 6, the {@code configure(AuthenticationManagerBuilder)} method is no longer
 * needed. The authentication provider is exposed as a {@code @Bean} here and explicitly registered
 * with the {@code AuthenticationManager} in {@link WebSecurityConfig#authenticationManagerBean()},
 * which creates a {@code ProviderManager} with this provider.</p>
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
