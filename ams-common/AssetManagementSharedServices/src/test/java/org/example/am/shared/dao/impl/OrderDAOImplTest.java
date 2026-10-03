package org.example.am.shared.dao.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import org.example.am.shared.dao.OrderDAO;
import org.example.am.shared.domain.Asset;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.domain.OrderType;
import org.example.am.shared.domain.ShippingCarrier;
import org.example.am.shared.helper.AbstractBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class OrderDAOImplTest extends AbstractBaseTest {

    @Autowired
    @Qualifier("orderSharedDAO")
    private OrderDAO orderDAO;

    @Test
    public void orderIsLoadedWithItsCodesResolved() {
        final Order order = orderDAO.getOrder(CUSTOMER_ID, 6002L);
        assertNotNull(order);
        assertEquals("ORD-6002", order.getOrderNumber());
        assertEquals(OrderType.REPLACEMENT, order.getOrderType());
        assertEquals(OrderStatusType.SUBMITTED, order.getOrderStatusType());
        assertEquals(ShippingCarrier.FEDEX, order.getShippingCarrier());
        assertTrue(order.isOpen());
    }

    @Test
    public void orderLookupIsScopedToTheCustomer() {
        assertNull(orderDAO.getOrder(OTHER_CUSTOMER_ID, 6002L));
    }

    @Test
    public void orderNumberLookupIsCaseInsensitiveAndCrossesCustomers() {
        assertNotNull(orderDAO.getOrderByOrderNumber("ord-6002"));
        assertNull(orderDAO.getOrderByOrderNumber("nope"));
        assertNull(orderDAO.getOrderByOrderNumber(null));
    }

    @Test
    public void openOrdersExcludeCompletedAndCancelled() {
        final List<Order> all = orderDAO.getOrdersForCustomer(CUSTOMER_ID);
        assertEquals(3, all.size());

        final List<Order> open = orderDAO.getOpenOrdersForCustomer(CUSTOMER_ID);
        assertEquals(1, open.size());
        assertEquals("ORD-6002", open.get(0).getOrderNumber());
    }

    @Test
    public void insertGeneratesTheKeyAndStampsItBackOntoTheOrder() {
        final Asset asset = new Asset();
        asset.setAssetId(Long.valueOf(HEALTHY_ASSET_ID));

        final Order order = new Order();
        order.setOrderNumber("ORD-NEW-1");
        order.setOrderType(OrderType.NEW_INSTALL);
        order.setOrderStatusType(OrderStatusType.SUBMITTED);
        order.setCustomerId(Long.valueOf(CUSTOMER_ID));
        order.setAsset(asset);
        order.setShippingCarrier(ShippingCarrier.UPS);
        order.setSubmittedDate(new Date());

        final long orderId = orderDAO.insertOrder(order, TEST_USER);
        assertTrue(orderId > 0);
        assertEquals(Long.valueOf(orderId), order.getOrderId());

        final Order reloaded = orderDAO.getOrder(CUSTOMER_ID, orderId);
        assertNotNull(reloaded);
        assertEquals(OrderType.NEW_INSTALL, reloaded.getOrderType());
        assertEquals(ShippingCarrier.UPS, reloaded.getShippingCarrier());
    }
}
