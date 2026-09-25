package org.example.am.shared.service;

import java.util.Date;
import java.util.List;

import org.example.am.shared.domain.Order;

/** Install orders: placing one, and reading them back. */
public interface OrderService {

    /** @return every order for the customer, newest first */
    List<Order> getOrdersForCustomer(long customerId);

    /**
     * Places a new-install order in one transaction: the site address and contact, the order row,
     * the device configuration, the installation visit and its calendar reservation, the audit
     * event and the queued confirmation email.
     *
     * <p>The appointment is taken through the scheduling port at the moment the order is placed.
     * If it filled up in the meantime the order still stands, unscheduled, and the installation's
     * timeslot is cleared on the order passed in - which is how the caller finds out.</p>
     *
     * @return the generated order id
     */
    long placeInstallOrder(Order order, String userId);

    /**
     * Reads an order together with what was captured when it was placed: the site address and
     * contact, the configuration and the installation with its timeslot.
     *
     * @return the populated order, or {@code null} when there is no such order for this customer
     */
    Order getOrderDetail(long customerId, long orderId);

    /**
     * @return the earliest day an installation may be booked for this order: its lead time in
     *         business days, counted from submission (or from now, before it is submitted)
     */
    Date getEarliestInstallationDate(Order order);
}
