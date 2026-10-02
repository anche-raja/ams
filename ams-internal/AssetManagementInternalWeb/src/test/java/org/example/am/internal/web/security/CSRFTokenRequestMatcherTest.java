package org.example.am.internal.web.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.example.am.internal.web.security.csrf.CSRFTokenRequestMatcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class CSRFTokenRequestMatcherTest {

    private static final String CONTEXT = "/AssetManagementInternalWeb";

    private CSRFTokenRequestMatcher matcher;

    @Before
    public void setUp() {
        matcher = new CSRFTokenRequestMatcher();
    }

    private static MockHttpServletRequest request(final String method, final String path) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, CONTEXT + path);
        request.setContextPath(CONTEXT);
        return request;
    }

    @Test
    public void safeMethodsNeedNoToken() {
        assertFalse(matcher.matches(request("GET", "/install/Site.action")));
        assertFalse(matcher.matches(request("HEAD", "/install/Site.action")));
        assertFalse(matcher.matches(request("OPTIONS", "/install/Site.action")));
        assertFalse(matcher.matches(request("TRACE", "/install/Site.action")));
    }

    @Test
    public void everyStateChangingMethodNeedsOne() {
        assertTrue(matcher.matches(request("POST", "/install/PlaceOrder.action")));
        assertTrue(matcher.matches(request("PUT", "/install/PlaceOrder.action")));
        assertTrue(matcher.matches(request("PATCH", "/install/PlaceOrder.action")));
        assertTrue(matcher.matches(request("DELETE", "/install/PlaceOrder.action")));
    }

    /** The container's monitor has no session to carry a token in. */
    @Test
    public void theHealthEndpointIsExempt() {
        assertFalse(matcher.matches(request("POST", "/health")));
        assertFalse(matcher.matches(request("POST", "/health.action")));
    }

    @Test
    public void theErrorPagesAreExempt() {
        assertFalse(matcher.matches(request("POST", "/CustomError.action")));
        assertFalse(matcher.matches(request("POST", "/Unauthorized.action")));
    }

    /**
     * The exemption is anchored, so a path that merely starts with an exempt one is still
     * protected.
     */
    @Test
    public void theExemptionCannotBeExtended() {
        assertTrue(matcher.matches(request("POST", "/healthy")));
        assertTrue(matcher.matches(request("POST", "/health.action.evil")));
        assertTrue(matcher.matches(request("POST", "/health/install/PlaceOrder.action")));
    }

    /** Matching is on the path within the application, so the context root cannot change it. */
    @Test
    public void theContextRootIsStripped() {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/other/health");
        request.setContextPath("/other");
        assertFalse(matcher.matches(request));
    }
}
