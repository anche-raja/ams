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
     * @EnableMethodSecurity is deliberately NOT here. It has to sit alongside the
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
     */
    @Override
    public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
        configurer.favorPathExtension(true)
                .ignoreAcceptHeader(false)
                .useRegisteredExtensionsOnly(false)
                .defaultContentType(org.springframework.http.MediaType.TEXT_HTML)
                .mediaType("html", org.springframework.http.MediaType.TEXT_HTML)
                .mediaType("json", org.springframework.http.MediaType.APPLICATION_JSON);
    }

    /**
     * Keeps the legacy {@code AntPathMatcher} URL matching.
     *
     * <p>Spring 5.3 switched the default to {@code PathPatternParser}, which does not do
     * suffix pattern matching. Every mapping in this application predates that change, so the
     * parser is explicitly cleared to fall back to the old matcher. Remove this only together with
     * the {@code .action} suffixes themselves.</p>
     */
    @Override
    public void configurePathMatch(final PathMatchConfigurer configurer) {
        configurer.setPatternParser(null);
    }
}
