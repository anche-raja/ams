package org.example.am.internal.web.config;

import org.example.am.internal.web.interceptors.SpecialCharacterInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
     * Method security is enabled in the root context (GlobalSecurityConfig), deliberately not
     * here. It has to sit alongside the method-security configuration it configures, which lives
     * in the root context. Enabling it in this DispatcherServlet context either fails to reach
     * that configuration - the application then refuses to start - or builds a second, competing
     * method security setup in this context.
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
     * Content negotiation.
     *
     * <p>Spring Framework 6 removed path-extension content negotiation
     * ({@code favorPathExtension}/{@code useRegisteredExtensionsOnly}): suffix-based negotiation is
     * no longer supported by the framework. The two DispatcherServlet endpoints here
     * ({@code /error}, {@code /invalidSessionError}) are suffixless and return HTML views, so the
     * default-content-type and explicit media-type mappings below are enough; the {@code .action}
     * AJAX endpoints are served by the Struts filter, not this servlet, so they are unaffected.</p>
     *
     * <p>TODO(migration): confirm no DispatcherServlet-served endpoint relied on {@code .json} /
     * {@code .html} suffix negotiation before adopting Spring 6's header-based negotiation
     * deliberately.</p>
     */
    @Override
    public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
        configurer.ignoreAcceptHeader(false)
                .defaultContentType(org.springframework.http.MediaType.TEXT_HTML)
                .mediaType("html", org.springframework.http.MediaType.TEXT_HTML)
                .mediaType("json", org.springframework.http.MediaType.APPLICATION_JSON);
    }

    /*
     * The old configurePathMatch override (which cleared the pattern parser to fall back to the
     * legacy ant-style matcher and suffix pattern matching) is removed. Spring Framework 6 uses
     * PathPatternParser exclusively and no longer supports that fallback. The two controller
     * mappings here are exact, suffixless paths, so they resolve identically under
     * PathPatternParser. The legacy .action suffix routing lives entirely in the Struts filter,
     * which is unaffected by this change.
     *
     * TODO(migration): the .action suffix routing is handled by Struts, not Spring MVC; verify no
     * DispatcherServlet mapping depended on the old suffix-matching behaviour.
     */
}
