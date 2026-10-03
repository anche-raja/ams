package org.example.am.internal.web.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lets browsers cache the static assets.
 *
 * <p>Spring Security sets {@code no-store} on every response, which is right for the application's
 * own pages but would make the browser re-fetch the whole Dojo build on every navigation. This
 * filter is mapped only to the asset paths and overrides the header for those.</p>
 *
 * <p>{@code private} rather than {@code public}: these responses are same for every user, but the
 * deployment sits behind a shared reverse proxy and there is no reason to have it cache them.</p>
 */
public class StaticContentCachingHeaderFilter implements Filter {

    private static final int MAX_AGE_SECONDS = 600;
    private static final String CACHE_CONTROL = "Cache-Control";

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        // Nothing to configure: the mapping in web.xml decides what this applies to.
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response,
            final FilterChain chain) throws IOException, ServletException {
        if (response instanceof HttpServletResponse) {
            // Set before the chain runs so the downstream security headers writer overwrites
            // nothing: whoever writes last wins, and for these paths that should be us.
            ((HttpServletResponse) response).setHeader(CACHE_CONTROL,
                    "max-age=" + MAX_AGE_SECONDS + ", private");
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Nothing to release.
    }
}
