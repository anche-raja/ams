package org.example.am.internal.web.config;

import org.example.am.internal.service.AmsUserDetailsService;
import org.example.am.internal.web.security.WebSealPreAuthenticatedAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Registers the authentication provider globally, so that method level security in the service
 * layer resolves against the same authorities as the URL rules do.
 *
 * <p>Method security is enabled with {@code @EnableMethodSecurity}, carrying the same three flags
 * across explicitly: pre/post annotations, JSR-250 ({@code @RolesAllowed}) and the legacy
 * {@code @Secured} annotation. {@code prePostEnabled} defaults to {@code true} in Spring Security 6
 * but the other two default to {@code false}, so both are stated.</p>
 *
 * <p>The authentication provider is now a plain {@code @Bean}; Spring Security 6 assembles the
 * {@code AuthenticationManager} from the available {@link AuthenticationProvider} beans rather than
 * through a builder callback.</p>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, jsr250Enabled = true, securedEnabled = true)
public class GlobalSecurityConfig {

    @Autowired
    private AmsUserDetailsService amsUserDetailsService;

    @Bean
    public WebSealPreAuthenticatedAuthenticationProvider webSealAuthenticationProvider() {
        // The only provider: there is no form login and no in-memory fallback, so a request that
        // does not arrive pre-authenticated cannot authenticate at all.
        return new WebSealPreAuthenticatedAuthenticationProvider(amsUserDetailsService);
    }
}
