package org.example.am.internal.web.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.example.am.internal.utils.InternalConstants;
import org.example.am.internal.web.model.InstallOrderModel;
import org.example.am.shared.domain.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Switching the selected customer has to discard work in progress that belonged to the previous
 * one.
 *
 * <p>The bug this pins down was not a refusal but a silent mis-attribution. An in-progress order
 * carries the customer id it was created with; nothing updated it when the operator switched
 * customer, so the header showed the newly selected customer while the model still pointed at the
 * old one. Against a customer that cannot order, that surfaced as "Ordering is not enabled for this
 * customer" on a customer that plainly could. Against two customers that both could order, it
 * surfaced as nothing at all - the order was simply placed for the wrong one.</p>
 */
public class CustomerSwitchSessionTest {

    /** Concrete subclass: the methods under test are protected on the abstract base. */
    private static final class TestAction extends BaseAction {
        private static final long serialVersionUID = 1L;

        @Override
        public void setCurrentCustomer(final Customer customer) {
            super.setCurrentCustomer(customer);
        }

        @Override
        public Long getCurrentCustomerId() {
            return super.getCurrentCustomerId();
        }
    }

    private TestAction action;
    private MockHttpServletRequest request;

    @BeforeEach
    public void setUp() {
        request = new MockHttpServletRequest();
        action = new TestAction();
        action.setServletRequest(request);
    }

    private static Customer customer(final long id) {
        final Customer customer = new Customer();
        customer.setCustomerId(Long.valueOf(id));
        return customer;
    }

    private Object orderModel() {
        return request.getSession(true).getAttribute(InternalConstants.SESSION_ORDER_MODEL);
    }

    private void startAnOrderFor(final long customerId) {
        action.setCurrentCustomer(customer(customerId));
        request.getSession(true).setAttribute(InternalConstants.SESSION_ORDER_MODEL,
                orderFor(customerId));
    }

    private static InstallOrderModel orderFor(final long customerId) {
        final InstallOrderModel model = new InstallOrderModel();
        model.setCustomerId(Long.valueOf(customerId));
        return model;
    }

    @Test
    public void switchingCustomerDiscardsTheOrderInProgress() {
        startAnOrderFor(1002L);
        assertNotNull(orderModel(), "precondition: an order is in progress");

        action.setCurrentCustomer(customer(1011L));

        assertNull(orderModel(), "the order started for 1002 must not survive the switch to 1011");
        assertEquals(Long.valueOf(1011L), action.getCurrentCustomerId());
    }

    @Test
    public void reselectingTheSameCustomerKeepsTheOrderInProgress() {
        startAnOrderFor(1011L);
        final Object before = orderModel();

        // Re-selecting the customer already being worked on is a no-op the operator does by
        // accident; throwing their half-filled order away for it would be its own bug.
        action.setCurrentCustomer(customer(1011L));

        assertSame(before, orderModel(), "the same customer must not discard the order");
    }

    @Test
    public void clearingTheCustomerDiscardsTheOrderInProgress() {
        startAnOrderFor(1011L);

        action.setCurrentCustomer(null);

        assertNull(orderModel());
        assertNull(action.getCurrentCustomerId());
    }

    @Test
    public void firstSelectionKeepsAnythingAlreadyThere() {
        // No customer selected yet, so there is no previous owner to protect against: whatever is
        // in the session was created under this same selection and must survive.
        request.getSession(true).setAttribute(InternalConstants.SESSION_ORDER_MODEL,
                orderFor(0L));

        action.setCurrentCustomer(customer(1011L));

        assertNotNull(orderModel());
    }
}
