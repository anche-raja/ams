package org.example.am.internal.web.config;

import org.example.am.internal.service.AmsUserDetailsService;
import org.example.am.internal.web.security.WebSealPreAuthenticatedAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

/**
 * Registers the authentication provider globally, so that method level security in the service
 * layer resolves against the same authorities as the URL rules do.
 *
 * <p>{@code @EnableGlobalMethodSecurity} belongs here rather than on {@link ServletConfig}. This is
 * the class that extends {@link GlobalMethodSecurityConfiguration}, and the annotation has to be
 * visible in the same context as that subclass - it is what supplies the subclass with which
 * annotations to honour. This configuration is imported by {@code RootConfig}, so it lives in the
 * root context; the annotation on the {@code DispatcherServlet} context could not reach it, and the
 * application would fail to start with "EnableGlobalMethodSecurity is required".</p>
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, jsr250Enabled = true, securedEnabled = true)
public class GlobalSecurityConfig extends GlobalMethodSecurityConfiguration {

    @Autowired
    private AmsUserDetailsService amsUserDetailsService;

    @Bean
    public WebSealPreAuthenticatedAuthenticationProvider webSealAuthenticationProvider() {
        return new WebSealPreAuthenticatedAuthenticationProvider(amsUserDetailsService);
    }

    @Override
    protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
        // The only provider: there is no form login and no in-memory fallback, so a request that
        // does not arrive pre-authenticated cannot authenticate at all.
        auth.authenticationProvider(webSealAuthenticationProvider());
    }
}
