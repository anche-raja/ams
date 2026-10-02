package org.example.am.internal.web.action;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.utils.InternalConstants;
import org.example.am.shared.domain.Customer;
import org.example.am.shared.domain.Order;
import org.example.am.shared.service.CustomerSearchService;
import org.example.am.shared.service.CustomerService;
import org.example.am.shared.service.OrderService;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * The home page: the customers an operator can order for, and the orders already placed for the
 * one they are working on. It is also where an install order starts.
 */
@Component("HomeAction")
@Scope("prototype")
public class HomeAction extends BaseAction {

    private static final long serialVersionUID = 1L;

    @Autowired
    private transient CustomerSearchService customerSearchService;

    @Autowired
    private transient CustomerService customerService;

    @Autowired
    private transient OrderService orderService;

    private List<Customer> customerList = new ArrayList<Customer>();
    private List<Order> orders = new ArrayList<Order>();
    private Long selectedCustomerId;

    public String home() throws Exception {
        final String denied = requireRole(SecurityRoleType.INT_SEARCH_CUSTOMERS);
        if (denied != null) {
            return denied;
        }
        loadHome();
        return Action.SUCCESS;
    }

    /**
     * Starts a new install order for the chosen customer.
     *
     * <p>Selecting the customer and starting the order are one step: the flow always acts for the
     * session's current customer, and switching customer discards any order that was in progress
     * for the previous one (see {@link BaseAction#setCurrentCustomer(Customer)}). An order already
     * in progress for the same customer is discarded too, because the button says "new".</p>
     */
    public String startInstallOrder() throws Exception {
        final String denied = requireRole(SecurityRoleType.INT_CREATE_ORDER);
        if (denied != null) {
            return denied;
        }
        final Customer customer = selectedCustomerId == null
                ? null : customerService.getCustomer(selectedCustomerId.longValue());
        if (customer == null) {
            addActionError("Choose a customer to order for.");
            loadHome();
            return Action.INPUT;
        }
        if (!customerService.isOrderingEnabled(customer.getCustomerId().longValue())) {
            addActionError(customer.getDisplayName() + " cannot place orders at the moment.");
            loadHome();
            return Action.INPUT;
        }
        setCurrentCustomer(customer);
        final HttpSession session = getOrCreateSession();
        session.removeAttribute(InternalConstants.SESSION_ORDER_MODEL);
        logger.info("User {} started an install order for customer {}", getUserId(),
                customer.getCustomerId());
        return Action.SUCCESS;
    }

    private void loadHome() {
        customerList = customerSearchService.listAll(CommonConstants.MAX_SEARCH_RESULTS);
        final Long customerId = getCurrentCustomerId();
        if (customerId != null && hasRole(SecurityRoleType.INT_VIEW_ORDER)) {
            orders = orderService.getOrdersForCustomer(customerId.longValue());
        }
    }

    public List<Customer> getCustomerList() {
        return customerList;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public Long getSelectedCustomerId() {
        return selectedCustomerId;
    }

    public void setSelectedCustomerId(final Long selectedCustomerId) {
        this.selectedCustomerId = selectedCustomerId;
    }
}
