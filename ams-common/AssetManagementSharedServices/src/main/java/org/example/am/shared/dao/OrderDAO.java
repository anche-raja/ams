package org.example.am.shared.dao;

import java.util.List;

import org.example.am.shared.domain.Order;

/** Reads and writes {@code AMS_ORDERS}. */
public interface OrderDAO {

    Order getOrder(long customerId, long orderId);

    Order getOrderByOrderNumber(String orderNumber);

    List<Order> getOrdersForCustomer(long customerId);

    List<Order> getOpenOrdersForCustomer(long customerId);

    /** @return the generated order id */
    long insertOrder(Order order, String userId);

    /**
     * Points the order at the maintenance window, configuration and despatch window created for it.
     *
     * <p>A second statement rather than part of the insert. The window and configuration rows carry
     * the order id as their own foreign key, so neither can be written until the order exists; the
     * despatch window is set here so that SHIP_WINDOW_ID is only ever populated once the
     * reservation ledger actually backs it.</p>
     */
    int linkOrderArtifacts(long orderId, Long maintenanceWindowId, Long configurationId,
            Long shippingWindowId, String userId);
}
