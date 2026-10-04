package org.example.am.internal.web.interceptors;

import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.utils.InternalConstants;
import org.example.am.internal.web.security.SpecialCharacterException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The Spring MVC counterpart of {@link ValidateSpecialCharacterInterceptor}.
 *
 * <p>Two frameworks serve requests in this application, so the same check has to exist on both
 * paths or one of them becomes a way around it. Unlike the Struts side this one does not accept
 * square brackets in parameter names: indexed properties are a Struts form binding mechanism, and
 * nothing reachable through Spring MVC here posts one, so the wider list would buy nothing.
 *
 * <p>This side throws rather than returning a result
 * name, because the only handlers it guards are the two error pages: there is no form to send the
 * user back to.</p>
 */
public class SpecialCharacterInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LogManager.getLogger(SpecialCharacterInterceptor.class);

    private static final Pattern WHITELIST =
            Pattern.compile(InternalConstants.SPECIAL_CHARACTER_WHITELIST);

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
            final Object handler) {
        for (final Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (!isAccepted(entry.getKey())) {
                throw rejected(request, entry.getKey());
            }
            final String[] values = entry.getValue();
            if (values == null) {
                continue;
            }
            for (final String value : values) {
                if (!isAccepted(value)) {
                    throw rejected(request, entry.getKey());
                }
            }
        }
        return true;
    }

    private static boolean isAccepted(final String value) {
        return value == null || WHITELIST.matcher(value).matches();
    }

    private static SpecialCharacterException rejected(final HttpServletRequest request,
            final String parameterName) {
        LOGGER.warn("Rejected request to {}: parameter '{}' contains unaccepted characters",
                request.getRequestURI(), parameterName);
        return new SpecialCharacterException(parameterName);
    }
}
