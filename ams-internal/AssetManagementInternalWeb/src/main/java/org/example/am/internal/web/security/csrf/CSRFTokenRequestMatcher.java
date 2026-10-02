package org.example.am.internal.web.security.csrf;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Decides which requests need a CSRF token.
 *
 * <p>Safe methods are exempt because they do not change state. The health endpoint is exempt because
 * it is polled by the container's own monitor, which has no session to carry a token in - and it
 * neither reads nor writes anything a forged request could exploit.</p>
 *
 * <p>Everything else - every POST, PUT, PATCH and DELETE, including the Struts AJAX endpoints -
 * requires a token. The AJAX endpoints additionally carry the per-session token this application
 * generates itself, so a forged AJAX POST has to defeat both.</p>
 */
public class CSRFTokenRequestMatcher implements RequestMatcher {

    private static final Set<String> SAFE_METHODS =
            new HashSet<String>(Arrays.asList("GET", "HEAD", "TRACE", "OPTIONS"));

    /** Paths that never need a token. Anchored, so nothing longer can slip past. */
    private static final Pattern EXEMPT_PATHS =
            Pattern.compile("^/(health(\\.action)?|CustomError\\.action|Unauthorized\\.action)$");

    @Override
    public boolean matches(final HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        return !EXEMPT_PATHS.matcher(getPath(request)).matches();
    }

    /**
     * @return the path within the application, so a change of context root cannot turn an exempt
     *         path into a protected one or the other way round
     */
    private static String getPath(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (contextPath != null && contextPath.length() > 0 && uri.startsWith(contextPath)) {
            final String path = uri.substring(contextPath.length());
            return path.length() == 0 ? "/" : path;
        }
        return uri;
    }
}
