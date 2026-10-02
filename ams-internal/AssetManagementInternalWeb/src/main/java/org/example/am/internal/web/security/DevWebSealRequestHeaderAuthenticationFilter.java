package org.example.am.internal.web.security;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.security.WebSealPrincipal;

/**
 * Stands in for the reverse proxy on a developer's machine.
 *
 * <p>There is no proxy in front of a local container, so without this every request would be
 * rejected and the application could not be run at all. It asserts a fixed developer identity with
 * a fixed set of groups.</p>
 *
 * <p><strong>Only registered for the {@code local}, {@code dev} and {@code fit} profiles.</strong>
 * {@code WebSecurityConfig} chooses between this and the real filter by profile, so it cannot be
 * reached in an environment that has a proxy in front of it. It also honours the real headers when
 * they are present, so a local run behind a proxy stub still behaves like production.</p>
 */
public class DevWebSealRequestHeaderAuthenticationFilter
        extends WebSealRequestHeaderAuthenticationFilter {

    private static final Logger LOGGER =
            LogManager.getLogger(DevWebSealRequestHeaderAuthenticationFilter.class);

    private String developerUsername = "devuser";
    private List<String> developerGroups = new ArrayList<String>();

    @Override
    protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
        final Object fromHeaders = super.getPreAuthenticatedPrincipal(request);
        if (fromHeaders != null) {
            return fromHeaders;
        }
        LOGGER.warn("No {} header present; asserting the local developer identity '{}'."
                + " This filter is only registered outside production.",
                HEADER_USER, developerUsername);

        final WebSealPrincipal principal = new WebSealPrincipal(developerUsername);
        principal.setFirstName("Local");
        principal.setLastName("Developer");
        principal.setEmailAddress(developerUsername + "@localhost");
        principal.setLdapGroups(developerGroups);
        return principal;
    }

    public void setDeveloperUsername(final String developerUsername) {
        this.developerUsername = developerUsername;
    }

    public void setDeveloperGroups(final List<String> developerGroups) {
        this.developerGroups = developerGroups == null
                ? new ArrayList<String>() : new ArrayList<String>(developerGroups);
    }
}
