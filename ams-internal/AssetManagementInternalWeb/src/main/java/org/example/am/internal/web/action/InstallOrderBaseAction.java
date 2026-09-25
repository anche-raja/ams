package org.example.am.internal.web.action;

import javax.servlet.http.HttpSession;

import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.utils.InternalConstants;
import org.example.am.internal.web.model.InstallOrderModel;
import org.example.am.shared.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared behaviour for the steps of the install order flow.
 *
 * <p>The flow spans three screens, so the partly completed order lives in the session between
 * them. Holding it there rather than persisting each step means an abandoned order leaves nothing
 * behind, at the cost of the model having to be found and stored on every request - which is what
 * {@link #getModel()} and {@link #storeModel(InstallOrderModel)} are for.</p>
 */
public abstract class InstallOrderBaseAction extends BaseAction {

    private static final long serialVersionUID = 1L;

    /**
     * Sent when a step is opened with no order in progress for an orderable customer; the flow's
     * struts configuration maps it back to the home page.
     */
    public static final String RESULT_NO_ORDER = "noOrder";

    /** Send a user who skipped ahead back to the step they missed. */
    public static final String RESULT_BACK_TO_SITE = "backToSite";
    public static final String RESULT_BACK_TO_DEVICE = "backToDevice";

    @Autowired
    private transient CustomerService customerService;

    /**
     * @return the in-progress order from the session, creating an empty one for the current
     *         customer on first entry
     */
    @Override
    public InstallOrderModel getModel() {
        final HttpSession session = getOrCreateSession();
        InstallOrderModel model =
                (InstallOrderModel) session.getAttribute(InternalConstants.SESSION_ORDER_MODEL);
        if (model == null) {
            model = new InstallOrderModel();
            model.setCustomerId(getCurrentCustomerId());
            session.setAttribute(InternalConstants.SESSION_ORDER_MODEL, model);
        }
        return model;
    }

    protected void storeModel(final InstallOrderModel model) {
        getOrCreateSession().setAttribute(InternalConstants.SESSION_ORDER_MODEL, model);
    }

    /**
     * Drops the in-progress order. Called once it is placed or abandoned, so that returning to the
     * flow starts cleanly rather than resuming a finished order.
     */
    protected void clearModel() {
        final HttpSession session = getSession();
        if (session != null) {
            session.removeAttribute(InternalConstants.SESSION_ORDER_MODEL);
        }
    }

    protected CustomerService getCustomerService() {
        return customerService;
    }

    /**
     * The check every step opens with: the role, then that the customer may still order.
     *
     * <p>The customer is re-checked on every step rather than only at the start: an operator can
     * leave a half-finished order open for a long time, and ordering may have been switched off for
     * that customer in the meantime.</p>
     *
     * @return {@code null} to carry on, otherwise the result name to return
     */
    protected String checkCanContinue(final InstallOrderModel model) {
        final String denied = requireRole(SecurityRoleType.INT_CREATE_ORDER);
        if (denied != null) {
            return denied;
        }
        if (model.getCustomerId() == null) {
            addActionError("Choose a customer on the home page before starting an order.");
            return RESULT_NO_ORDER;
        }
        if (!customerService.isOrderingEnabled(model.getCustomerId().longValue())) {
            addActionError("Ordering is not enabled for this customer.");
            return RESULT_NO_ORDER;
        }
        return null;
    }

    /**
     * {@link #checkCanContinue(InstallOrderModel)}, plus that the steps before this one have been
     * completed. Struts will route a bookmarked or hand-typed URL to any step, and the later steps
     * depend on what the earlier ones collected.
     */
    protected String checkStepReached(final InstallOrderModel model, final int step) {
        final String stop = checkCanContinue(model);
        if (stop != null) {
            return stop;
        }
        if (model.getFurthestStepReached() >= step) {
            return null;
        }
        return model.getFurthestStepReached() < InstallOrderModel.STEP_DEVICE
                ? RESULT_BACK_TO_SITE : RESULT_BACK_TO_DEVICE;
    }
}
