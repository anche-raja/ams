package org.example.am.internal.web.security;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.security.WebSealPrincipal;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

/**
 * Reads the identity the reverse proxy asserted.
 *
 * <p>The proxy terminates authentication and forwards the request with {@code iv-user} and
 * {@code iv-groups} headers. The application is only reachable through the proxy - the container
 * listens on an internal address that nothing else can route to - which is what makes trusting
 * these headers safe. If the application were ever exposed directly, anyone could set them, so the
 * network placement is part of the security control, not an afterthought.</p>
 */
public class WebSealRequestHeaderAuthenticationFilter
        extends AbstractPreAuthenticatedProcessingFilter {

    private static final Logger LOGGER =
            LogManager.getLogger(WebSealRequestHeaderAuthenticationFilter.class);

    public static final String HEADER_USER = "iv-user";
    public static final String HEADER_GROUPS = "iv-groups";
    public static final String HEADER_EMAIL = "iv-email";
    public static final String HEADER_FIRST_NAME = "iv-firstname";
    public static final String HEADER_LAST_NAME = "iv-lastname";
    public static final String HEADER_ACCOUNT = "iv-account";

    /** The proxy sends an unauthenticated caller through as this literal. */
    private static final String UNAUTHENTICATED = "Unauthenticated";

    @Override
    protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
        final String username = trimToNull(request.getHeader(HEADER_USER));
        if (username == null || UNAUTHENTICATED.equalsIgnoreCase(username)) {
            LOGGER.debug("No {} header on {}", HEADER_USER, request.getRequestURI());
            return null;
        }

        final WebSealPrincipal principal = new WebSealPrincipal(username);
        principal.setEmailAddress(trimToNull(request.getHeader(HEADER_EMAIL)));
        principal.setFirstName(trimToNull(request.getHeader(HEADER_FIRST_NAME)));
        principal.setLastName(trimToNull(request.getHeader(HEADER_LAST_NAME)));
        principal.setAccountNumber(trimToNull(request.getHeader(HEADER_ACCOUNT)));
        principal.setLdapGroups(parseGroups(request.getHeader(HEADER_GROUPS)));
        return principal;
    }

    /**
     * There is no credential to check: the proxy already did that. A constant is returned so the
     * pre-authenticated provider has something non-null to work with.
     */
    @Override
    protected Object getPreAuthenticatedCredentials(final HttpServletRequest request) {
        return "N/A";
    }

    /**
     * Parses the {@code iv-groups} header.
     *
     * <p>The proxy sends a comma separated list, quoting any group whose name contains a comma, and
     * has been observed to send a trailing empty element. Both are handled here so the DAO can
     * assume a clean list.</p>
     */
    static List<String> parseGroups(final String header) {
        final List<String> groups = new ArrayList<String>();
        if (header == null || header.trim().length() == 0) {
            return groups;
        }

        // Scanned rather than split, because a distinguished name contains commas of its own and
        // splitting on them first would tear one group into several.
        final StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < header.length(); i++) {
            final char character = header.charAt(i);
            if (character == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (character == ',' && !inQuotes) {
                addGroup(groups, current);
                continue;
            }
            current.append(character);
        }
        addGroup(groups, current);
        return groups;
    }

    private static void addGroup(final List<String> groups, final StringBuilder current) {
        final String group = current.toString().trim();
        current.setLength(0);
        if (group.length() > 0) {
            groups.add(group);
        }
    }

    private static String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }
}
