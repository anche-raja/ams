package org.example.am.internal.web.interceptors;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.example.am.internal.web.security.SpecialCharacterException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class SpecialCharacterInterceptorTest {

    private SpecialCharacterInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Before
    public void setUp() {
        interceptor = new SpecialCharacterInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    public void ordinaryInputPasses() {
        request.addParameter("searchTerm", "AMS-000501");
        request.addParameter("comments", "Replace by Friday, contact Dana (x204) re: the move.");
        request.addParameter("emailAddress", "dana.whitfield@example.test");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    public void anEmptyRequestPasses() {
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    public void angleBracketsAreRejected() {
        request.addParameter("searchTerm", "<script>alert(1)</script>");
        expectRejection("searchTerm");
    }

    /** The semicolon is deliberately outside the whitelist. */
    @Test
    public void semicolonsAreRejected() {
        request.addParameter("comments", "one; two");
        expectRejection("comments");
    }

    @Test
    public void quotesAreRejected() {
        request.addParameter("comments", "\" OR 1=1 --");
        expectRejection("comments");
    }

    @Test
    public void backslashesAreRejected() {
        request.addParameter("comments", "C:\\Windows\\System32");
        expectRejection("comments");
    }

    @Test
    public void aHostileParameterNameIsRejectedAsWellAsAHostileValue() {
        request.addParameter("<bad>", "harmless");
        expectRejection("<bad>");
    }

    @Test
    public void controlCharactersAreRejected() {
        request.addParameter("searchTerm", "abc\u0007def");
        expectRejection("searchTerm");
    }

    /**
     * The rejected value must not travel on the exception, or it can be echoed back into whatever
     * renders the error.
     */
    @Test
    public void theRejectedValueIsNotCarriedOnTheException() {
        request.addParameter("searchTerm", "<script>alert(1)</script>");
        try {
            interceptor.preHandle(request, response, null);
            fail("Expected the request to be rejected");
        } catch (final SpecialCharacterException rejected) {
            assertTrue(!rejected.getMessage().contains("script"));
            assertTrue(rejected.getMessage().contains("searchTerm"));
        }
    }

    private void expectRejection(final String parameterName) {
        try {
            interceptor.preHandle(request, response, null);
            fail("Expected " + parameterName + " to be rejected");
        } catch (final SpecialCharacterException rejected) {
            assertTrue(rejected.getParameterName().equals(parameterName));
        }
    }
}
