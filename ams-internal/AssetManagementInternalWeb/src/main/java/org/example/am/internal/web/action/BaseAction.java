package org.example.am.internal.web.action;

import java.util.Date;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.example.am.internal.security.AmsUser;
import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.utils.InternalConstants;
import org.example.am.internal.web.model.JsonErrorModel;
import org.example.am.shared.domain.Customer;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.ModelDriven;

/**
 * The common ground for every action in the application.
 *
 * <p>Four things live here because they are needed almost everywhere and are easy to get subtly
 * wrong in isolation: the role check, access to the authenticated user and the customer being
 * worked on, the request-scoped clock, and the non-production banner.</p>
 */
public abstract class BaseAction extends ActionSupport
        implements ServletRequestAware, ServletResponseAware, ModelDriven<Object> {

    private static final long serialVersionUID = 1L;

    protected final transient Logger logger = LogManager.getLogger(getClass());

    /** Result name returned when the user lacks the role an action requires. */
    public static final String UNAUTHORIZED = InternalConstants.RESULT_UNAUTHORIZED;

    private transient HttpServletRequest servletRequest;
    private transient HttpServletResponse servletResponse;

    @Autowired
    private transient ConfigService configService;

    @Override
    public void setServletRequest(final HttpServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    @Override
    public void setServletResponse(final HttpServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }

    protected HttpServletRequest getServletRequest() {
        return servletRequest;
    }

    protected HttpServletResponse getServletResponse() {
        return servletResponse;
    }

    protected HttpSession getSession() {
        return servletRequest == null ? null : servletRequest.getSession(false);
    }

    protected HttpSession getOrCreateSession() {
        return servletRequest.getSession(true);
    }

    protected ConfigService getConfigService() {
        return configService;
    }

    // ------------------------------------------------------------------
    // Authorisation
    // ------------------------------------------------------------------

    /**
     * The authorisation check every action makes.
     *
     * <p>Delegates to the container rather than to the {@code SecurityContext}, because the role
     * model is fine grained enough that the URL rules cannot express it: the security chain proves
     * the caller is authenticated, and this decides what they may then do.</p>
     *
     * @param role the role to require
     * @return {@code true} when the caller holds it
     */
    protected boolean hasRole(final SecurityRoleType role) {
        if (role == null || servletRequest == null) {
            return false;
        }
        return servletRequest.isUserInRole(role.getCode());
    }

    /**
     * Convenience for the common "check, or bail out" shape.
     *
     * @return {@code null} when the caller holds the role, or the {@code unauthorized} result name
     *         to return from the action method
     */
    protected String requireRole(final SecurityRoleType role) {
        if (hasRole(role)) {
            return null;
        }
        logger.warn("User {} attempted {} without {}", getUserId(),
                getClass().getSimpleName(), role == null ? "a role" : role.getCode());
        return UNAUTHORIZED;
    }

    /** @return the authenticated user, or {@code null} if somehow not authenticated */
    protected AmsUser getAmsUser() {
        final Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AmsUser)) {
            return null;
        }
        return (AmsUser) authentication.getPrincipal();
    }

    protected String getUserId() {
        final AmsUser user = getAmsUser();
        if (user != null) {
            return user.getUsername();
        }
        return servletRequest == null ? null : servletRequest.getRemoteUser();
    }

    /**
     * @return the customer the operator is currently acting on behalf of, or {@code null} before
     *         one has been chosen. Internal users are not tied to a customer, so this is session
     *         state, not an attribute of the account.
     */
    /**
     * Public because the page header renders it on every screen through OGNL, which only sees
     * public accessors. Every action inherits it, so the header never has to know which action
     * served the page.
     */
    public Customer getCurrentCustomer() {
        final HttpSession session = getSession();
        if (session == null) {
            return null;
        }
        return (Customer) session.getAttribute(InternalConstants.SESSION_CUSTOMER);
    }

    /**
     * Switches the customer the session is acting for, discarding any work in progress that
     * belonged to the previous one.
     *
     * <p>The discard is not housekeeping, it is correctness. An in-progress order carries its own
     * {@code customerId}, copied in when the model was first created and never
     * updated afterwards. Leaving one in place across a customer switch means every screen shows
     * the newly selected customer while the model still points at the old one - so the work is
     * either refused for reasons that make no sense against the customer on screen, or, when both
     * customers can order, submitted against the wrong one with nothing to indicate it.</p>
     *
     * <p>Discarding rather than re-pointing is deliberate: a half-filled order is made of contacts,
     * addresses and an asset that all belong to the customer it was started for, none of which are
     * meaningful for a different one.</p>
     */
    protected void setCurrentCustomer(final Customer customer) {
        final HttpSession session = getOrCreateSession();
        final Long previousId = getCurrentCustomerId();
        final Long newId = customer == null ? null : customer.getCustomerId();

        if (previousId != null && !previousId.equals(newId)) {
            session.removeAttribute(InternalConstants.SESSION_ORDER_MODEL);
        }
        session.setAttribute(InternalConstants.SESSION_CUSTOMER, customer);
    }

    protected Long getCurrentCustomerId() {
        final Customer customer = getCurrentCustomer();
        return customer == null ? null : customer.getCustomerId();
    }

    // ------------------------------------------------------------------
    // AJAX token
    // ------------------------------------------------------------------

    /**
     * @return the per-session token the JSPs render into the page for the Dojo grids to send back
     */
    public String getAjaxToken() {
        final HttpSession session = getSession();
        if (session == null) {
            return null;
        }
        final Object token = session.getAttribute(InternalConstants.SESSION_AJAX_TOKEN);
        return token == null ? null : String.valueOf(token);
    }

    /**
     * @return {@code true} when the request carries the session's AJAX token. The interceptors do
     *         this check for the endpoints they guard; this is for an action that needs to make it
     *         part of a larger decision.
     */
    protected boolean isAjaxTokenValid() {
        final String expected = getAjaxToken();
        if (expected == null || servletRequest == null) {
            return false;
        }
        return expected.equals(servletRequest.getParameter(InternalConstants.PARAM_AJAX_TOKEN));
    }

    /**
     * The body of the global {@code invalid.json.token} result, which names this property as its
     * root. It says the session is gone, so {@code js/common.js} reloads the page rather than
     * trying to render an error inside the widget that made the call.
     */
    public JsonErrorModel getJsonError() {
        return JsonErrorModel.invalidSession();
    }

    // ------------------------------------------------------------------
    // The request-scoped clock
    // ------------------------------------------------------------------

    /**
     * The time every rule in this request should evaluate against.
     *
     * <p>Every action reads the clock through here rather than calling {@code new Date()}, so that
     * "now" is one seam to move in a test rather than a call scattered through each action.</p>
     */
    protected Date getCurrentTime() {
        return new Date();
    }

    // ------------------------------------------------------------------
    // Presentation helpers
    // ------------------------------------------------------------------

    /**
     * @return the banner text warning that this is not production, or {@code null} in production.
     *         Rendered by the page template on every screen.
     */
    public String getNonProductionBanner() {
        if (configService == null || !configService.isNonProductionEnvironment()) {
            return null;
        }
        return configService.getString(PropertyType.NON_PROD_BANNER_TEXT,
                "You are working in the " + configService.getEnvironmentName()
                + " environment. Changes here do not affect production.");
    }

    public boolean isNonProductionEnvironment() {
        return getNonProductionBanner() != null;
    }

    /**
     * Looks a message up in the application bundle.
     *
     * <p>Falls back to the key rather than throwing: a missing message should show as a visibly
     * wrong label, not take the page down.</p>
     */
    protected String getMessage(final String key, final String defaultMessage) {
        try {
            final ResourceBundle bundle =
                    ResourceBundle.getBundle(InternalConstants.BUNDLE_MESSAGES, getLocale());
            return bundle.getString(key);
        } catch (final MissingResourceException missing) {
            logger.debug("No message for key {}", key);
            return defaultMessage == null ? key : defaultMessage;
        }
    }

    /**
     * Public because {@code LocaleProvider} declares it so; the locale comes from the request
     * rather than from the JVM default, which on a server is whatever the container was started
     * with.
     */
    @Override
    public Locale getLocale() {
        return servletRequest == null ? Locale.getDefault() : servletRequest.getLocale();
    }

    /**
     * Adds an error against a form field and logs it, so a validation failure a user reports is
     * findable in the log.
     */
    protected void addFieldErrorAndLog(final String field, final String message) {
        addFieldError(field, message);
        logger.info("Validation failure on {}.{}: {}", getClass().getSimpleName(), field, message);
    }

    /**
     * Most actions are model driven; those that are not override this.
     */
    @Override
    public Object getModel() {
        return this;
    }

    /**
     * @param value a request parameter that should be a number
     * @return the parsed value, or {@code null} when it is absent or not a number
     */
    protected static Long toLong(final String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (final NumberFormatException notANumber) {
            return null;
        }
    }
}
