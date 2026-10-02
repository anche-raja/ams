package org.example.am.internal.web.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.example.am.internal.security.WebSealPrincipal;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class WebSealRequestHeaderAuthenticationFilterTest {

    private WebSealRequestHeaderAuthenticationFilter filter;

    @Before
    public void setUp() {
        filter = new WebSealRequestHeaderAuthenticationFilter();
    }

    @Test
    public void theIdentityIsReadFromTheHeaders() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("iv-user", "opsuser");
        request.addHeader("iv-groups", "AMS_INTERNAL_OPERATIONS,AMS_INTERNAL_ADMIN");
        request.addHeader("iv-email", "ops.user@example.test");
        request.addHeader("iv-firstname", "Ops");
        request.addHeader("iv-lastname", "User");

        final WebSealPrincipal principal =
                (WebSealPrincipal) filter.getPreAuthenticatedPrincipal(request);

        assertNotNull(principal);
        assertEquals("opsuser", principal.getUsername());
        assertEquals("ops.user@example.test", principal.getEmailAddress());
        assertEquals("Ops User", principal.getDisplayName());
        assertEquals(2, principal.getLdapGroups().size());
    }

    @Test
    public void aRequestWithNoUserHeaderIsNotAuthenticated() {
        assertNull(filter.getPreAuthenticatedPrincipal(new MockHttpServletRequest()));
    }

    /** The proxy sends an unauthenticated caller through under this literal. */
    @Test
    public void theUnauthenticatedLiteralIsNotAnIdentity() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("iv-user", "Unauthenticated");
        assertNull(filter.getPreAuthenticatedPrincipal(request));

        final MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader("iv-user", "   ");
        assertNull(filter.getPreAuthenticatedPrincipal(blank));
    }

    @Test
    public void groupsAreSplitOnCommas() {
        final List<String> groups = WebSealRequestHeaderAuthenticationFilter
                .parseGroups("ALPHA,BETA,GAMMA");
        assertEquals(3, groups.size());
        assertEquals("ALPHA", groups.get(0));
        assertEquals("GAMMA", groups.get(2));
    }

    /** The proxy quotes any group whose name contains a comma. */
    @Test
    public void quotedGroupNamesAreUnwrapped() {
        final List<String> groups = WebSealRequestHeaderAuthenticationFilter
                .parseGroups("\"CN=Ops, OU=Teams\",PLAIN_GROUP");
        assertEquals(2, groups.size());
        // The comma inside the quoted name must not have split it.
        assertEquals("CN=Ops, OU=Teams", groups.get(0));
        assertEquals("PLAIN_GROUP", groups.get(1));
    }

    /** A trailing empty element has been seen in the wild; it must not become a group. */
    @Test
    public void emptyElementsAreDropped() {
        assertEquals(2, WebSealRequestHeaderAuthenticationFilter
                .parseGroups("ALPHA, ,BETA,").size());
        assertTrue(WebSealRequestHeaderAuthenticationFilter.parseGroups("").isEmpty());
        assertTrue(WebSealRequestHeaderAuthenticationFilter.parseGroups(null).isEmpty());
        assertTrue(WebSealRequestHeaderAuthenticationFilter.parseGroups("  ").isEmpty());
    }

    @Test
    public void thereIsNoCredentialToVerify() {
        // The proxy already authenticated the caller; a non-null constant keeps the provider happy.
        assertNotNull(filter.getPreAuthenticatedCredentials(new MockHttpServletRequest()));
    }
}
