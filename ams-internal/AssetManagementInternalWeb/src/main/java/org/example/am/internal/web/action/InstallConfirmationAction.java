package org.example.am.internal.web.action;

import org.example.am.internal.security.SecurityRoleType;
import org.example.am.shared.domain.Order;
import org.example.am.shared.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * The receipt shown after an order is placed, and the view of any order from the home page.
 *
 * <p>It reads from the database rather than from the session, so what it shows is what was
 * actually written - including an appointment that could not be reserved, which is simply absent.
 * The redirect from the place step says when that happened, so the page can explain it.</p>
 */
@Component("InstallConfirmationAction")
@Scope("prototype")
public class InstallConfirmationAction extends BaseAction {

    private static final long serialVersionUID = 1L;

    @Autowired
    private transient OrderService orderService;

    private Long orderId;
    private Order order;
    private boolean appointmentLost;

    public String confirmation() throws Exception {
        final String denied = requireRole(SecurityRoleType.INT_VIEW_ORDER);
        if (denied != null) {
            return denied;
        }
        final Long customerId = getCurrentCustomerId();
        if (orderId == null || customerId == null) {
            addActionError("That order could not be found.");
            return Action.ERROR;
        }
        // Scoped to the session's customer, so an order id typed into the URL cannot show another
        // customer's order.
        order = orderService.getOrderDetail(customerId.longValue(), orderId.longValue());
        if (order == null) {
            addActionError("That order could not be found.");
            return Action.ERROR;
        }
        return Action.SUCCESS;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(final Long orderId) {
        this.orderId = orderId;
    }

    public Order getOrder() {
        return order;
    }

    public boolean isAppointmentLost() {
        return appointmentLost;
    }

    public void setAppointmentLost(final boolean appointmentLost) {
        this.appointmentLost = appointmentLost;
    }
}
