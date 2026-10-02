package org.example.am.internal.web.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.logging.LoggingConstants;
import org.example.am.shared.logging.LoggingUtils;

/**
 * Stamps the caller and the activity type onto the logging context for the life of the request.
 *
 * <p>Every log line written while serving the request then carries the user id, which is what makes
 * a production log searchable by who was affected rather than only by what failed.</p>
 *
 * <p>The context is cleared in a {@code finally} block without exception: the container pools
 * request threads, so a key left behind would be attributed to whoever that thread serves next.</p>
 */
public class LoggingFilter implements Filter {

    private static final Logger LOGGER = LogManager.getLogger(LoggingFilter.class);

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        LOGGER.info("Request logging filter initialised");
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response,
            final FilterChain chain) throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest) {
                final HttpServletRequest httpRequest = (HttpServletRequest) request;
                LoggingUtils.setUserId(httpRequest.getRemoteUser());
                LoggingUtils.setActivityType(LoggingConstants.ACTIVITY_HTTP);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
                }
            }
            chain.doFilter(request, response);
        } finally {
            LoggingUtils.clear();
        }
    }

    @Override
    public void destroy() {
        LoggingUtils.clear();
    }
}
