package org.example.am.internal.web.interceptors;

import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.example.am.internal.utils.InternalConstants;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

/**
 * Requires the per-session AJAX token on the endpoints the grids post to.
 *
 * <p>The token is issued by {@code AjaxTokenListener} when the session starts and rendered into the
 * page; a request that does not carry the matching value did not come from a page this application
 * served. Comparison is constant time, so a caller cannot narrow the token down by timing repeated
 * guesses.</p>
 */
public class AjaxTokenInterceptor extends AbstractInterceptor {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LogManager.getLogger(AjaxTokenInterceptor.class);

    @Override
    public String intercept(final ActionInvocation invocation) throws Exception {
        final HttpSession session = ServletActionContext.getRequest().getSession(false);
        if (session == null) {
            LOGGER.info("Rejecting {}: the session has gone", invocation.getProxy().getActionName());
            return getInvalidTokenResult();
        }

        final Object expected = session.getAttribute(InternalConstants.SESSION_AJAX_TOKEN);
        final String supplied =
                ServletActionContext.getRequest().getParameter(InternalConstants.PARAM_AJAX_TOKEN);

        if (expected == null || !constantTimeEquals(String.valueOf(expected), supplied)) {
            LOGGER.warn("Rejecting {}: the AJAX token did not match",
                    invocation.getProxy().getActionName());
            return getInvalidTokenResult();
        }
        return invocation.invoke();
    }

    /**
     * @return the result name to return when the token is missing or wrong; overridden by the JSON
     *         variant so that a grid gets a JSON body rather than an HTML page
     */
    protected String getInvalidTokenResult() {
        return InternalConstants.RESULT_INVALID_TOKEN;
    }

    /**
     * Compares without short circuiting on the first differing character.
     */
    private static boolean constantTimeEquals(final String expected, final String supplied) {
        if (supplied == null || expected.length() != supplied.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= expected.charAt(i) ^ supplied.charAt(i);
        }
        return difference == 0;
    }
}
