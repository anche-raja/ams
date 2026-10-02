package org.example.am.internal.web.interceptors;

import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

/**
 * Sets the response security headers on the Struts path.
 *
 * <p>These duplicate what Spring Security's headers writer sets. The duplication is deliberate: the
 * Struts filter can complete a response without the Spring Security chain having written its
 * headers for that dispatch, and a page served without these is a page served without
 * clickjacking, sniffing and framing protection. Setting them twice costs nothing; missing them
 * once does not.</p>
 */
public class SecurityHeadersInterceptor extends AbstractInterceptor {

    private static final long serialVersionUID = 1L;

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "frame-ancestors 'self'; "
            + "form-action 'self'";

    @Override
    public String intercept(final ActionInvocation invocation) throws Exception {
        final HttpServletResponse response = ServletActionContext.getResponse();
        if (response != null && !response.isCommitted()) {
            response.setHeader("X-Frame-Options", "SAMEORIGIN");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            response.setHeader("Referrer-Policy", "same-origin");
            response.setHeader("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains");
            response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        }
        return invocation.invoke();
    }
}
