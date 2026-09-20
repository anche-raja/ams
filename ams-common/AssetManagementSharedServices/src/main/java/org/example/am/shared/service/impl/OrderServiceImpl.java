package org.example.am.shared.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.dao.AddressDAO;
import org.example.am.shared.dao.AssetConfigDAO;
import org.example.am.shared.dao.ContactDAO;
import org.example.am.shared.dao.MaintenanceWindowDAO;
import org.example.am.shared.dao.OrderDAO;
import org.example.am.shared.dao.RequestDAO;
import org.example.am.shared.dao.StoredProcedureDAO;
import org.example.am.shared.dao.SubscriberPcDAO;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.AssetConfiguration;
import org.example.am.shared.domain.Contact;
import org.example.am.shared.domain.ContactType;
import org.example.am.shared.domain.EmailEntityType;
import org.example.am.shared.domain.EmailTemplateType;
import org.example.am.shared.domain.EventType;
import org.example.am.shared.domain.FacilitationCallType;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.domain.MaintenanceWindow;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.service.CalendarService;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.service.OrderService;
import org.example.am.shared.service.ShippingCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("orderService")
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LogManager.getLogger(OrderServiceImpl.class);

    /** Used when the property table has no value; matches the contractual default. */
    private static final int DEFAULT_MIN_HOURS_TO_CANCEL = 48;

    /** The success status every AMS scheduling procedure returns. */
    private static final String STATUS_OK =
            org.example.am.shared.dao.scheduling.SchedulingStatus.OK;

    @Autowired
    private OrderDAO orderDAO;

    @Autowired
    private RequestDAO requestDAO;

    @Autowired
    private StoredProcedureDAO storedProcedureDAO;

    @Autowired
    private ConfigService configService;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private AddressDAO addressDAO;

    @Autowired
    private ContactDAO contactDAO;

    @Autowired
    private MaintenanceWindowDAO maintenanceWindowDAO;

    @Autowired
    private AssetConfigDAO assetConfigDAO;

    @Autowired
    private SubscriberPcDAO subscriberPcDAO;

    @Autowired
    private ShippingCalendarService shippingCalendarService;

    @Override
    public Order getOrder(final long customerId, final long orderId) {
        return orderDAO.getOrder(customerId, orderId);
    }

    @Override
    public List<Order> getOpenOrders(final long customerId) {
        return orderDAO.getOpenOrdersForCustomer(customerId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public long submitOrder(final Order order, final String userId) {
        order.setOrderStatusType(OrderStatusType.SUBMITTED);
        if (order.getSubmittedDate() == null) {
            order.setSubmittedDate(order.getCurrentTime());
        }

        // The address and the three contacts are written first because the order row carries their
        // ids. Everything else has to wait until the order exists, because it carries the order's.
        persistShippingAddress(order, userId);
        persistContacts(order, userId);

        final long orderId = orderDAO.insertOrder(order, userId);

        final Long maintenanceWindowId = persistMaintenanceWindow(order, orderId, userId);
        final Long configurationId = persistConfiguration(order, orderId, userId);
        subscriberPcDAO.replaceSubscriberPcs(order.getSubscriberPcs(), orderId, userId);
        final Long shippingWindowId = reserveShippingWindow(order, orderId, userId);

        orderDAO.linkOrderArtifacts(orderId, maintenanceWindowId, configurationId, shippingWindowId,
                userId);

        requestDAO.recordEvent(EventType.ORDER_SUBMITTED, EmailEntityType.ORDER, orderId,
                "Order " + order.getOrderNumber() + " submitted", userId);
        // Queued, not sent: the poller delivers it after this transaction commits, so a mail
        // failure can never roll the order back.
        storedProcedureDAO.addEntityEmail(EmailEntityType.ORDER.getCode(), orderId,
                EmailTemplateType.ORDER_CONFIRMATION.getCode(), userId);
        return orderId;
    }

    /**
     * Writes the shipping address unless it is one already on file.
     *
     * <p>A new row every time, rather than updating an existing one: the address is a record of
     * where this order was sent, and editing it later would rewrite the history of orders that
     * have already shipped.</p>
     */
    private void persistShippingAddress(final Order order, final String userId) {
        final Address address = order.getShippingAddress();
        if (address == null) {
            return;
        }
        if (address.getAddressId() == null) {
            addressDAO.insertAddress(address, userId);
        }
        order.setShippingAddress(address);
    }

    private void persistContacts(final Order order, final String userId) {
        order.setOrderingContact(persistContact(order.getOrderingContact(), ContactType.ORDERING,
                order.getCustomerId(), userId));
        order.setShippingContact(persistContact(order.getShippingContact(), ContactType.SHIPPING,
                order.getCustomerId(), userId));
        order.setInstallationContact(persistContact(order.getInstallationContact(),
                ContactType.INSTALLATION, order.getCustomerId(), userId));
    }

    /**
     * @return the contact with its id populated, or {@code null} when there was nothing to write
     */
    private Contact persistContact(final Contact contact, final ContactType contactType,
            final Long customerId, final String userId) {
        if (contact == null) {
            return null;
        }
        if (contact.getContactId() != null) {
            // Picked from the customer's existing contacts; nothing to write.
            return contact;
        }
        // The role belongs to the contact rather than to the order, so it is stamped on here
        // rather than being left to whichever screen happened to build the object.
        contact.setContactType(contactType);
        contact.setCustomerId(customerId);
        contact.setActive(true);
        contactDAO.insertContact(contact, userId);
        return contact;
    }

    private Long persistMaintenanceWindow(final Order order, final long orderId,
            final String userId) {
        final MaintenanceWindow window = order.getMaintenanceWindow();
        if (window == null || window.getDayType() == null) {
            return null;
        }
        maintenanceWindowDAO.insertMaintenanceWindowForOrder(window, orderId, userId);
        return window.getMaintenanceWindowId();
    }

    private Long persistConfiguration(final Order order, final long orderId, final String userId) {
        final AssetConfiguration configuration = order.getAssetConfiguration();
        if (configuration == null) {
            return null;
        }
        assetConfigDAO.insertOrderConfiguration(configuration, orderId, userId);
        return configuration.getConfigurationId();
    }

    /**
     * Takes a place in the chosen despatch window.
     *
     * <p>Returns {@code null} rather than throwing when the window has filled up in the meantime.
     * There is no review step in front of this: throwing would discard everything the user keyed
     * across six screens because a warehouse slot filled while they were typing. The order is
     * placed without a window instead, {@code SHIP_WINDOW_ID} stays null so nothing claims capacity
     * it does not hold, and the confirmation screen says so.</p>
     */
    private Long reserveShippingWindow(final Order order, final long orderId, final String userId) {
        final Long timeslotId = order.getShippingWindowTimeslotId();
        if (timeslotId == null) {
            return null;
        }
        final String status = storedProcedureDAO.reserveTimeslot(timeslotId.longValue(), orderId,
                FacilitationCallType.SHIPPING.getCode(),
                order.getShippingWindow() == null ? null : order.getShippingWindow().getStartTime(),
                userId);
        if (!STATUS_OK.equals(status)) {
            LOGGER.info("Despatch window {} could not be reserved for order {}: {}", timeslotId,
                    Long.valueOf(orderId), status);
            order.setShippingWindow(null);
            order.setShippingWindowTimeslotId(null);
            return null;
        }
        return timeslotId;
    }

    @Override
    public Order getOrderDetail(final long customerId, final long orderId) {
        final Order order = orderDAO.getOrder(customerId, orderId);
        if (order == null) {
            return null;
        }
        if (order.getShipAddressId() != null) {
            order.setShippingAddress(addressDAO.getAddress(order.getShipAddressId().longValue()));
        }
        if (order.getOrderingContactId() != null) {
            order.setOrderingContact(contactDAO.getContact(order.getOrderingContactId().longValue()));
        }
        if (order.getShippingContactId() != null) {
            order.setShippingContact(contactDAO.getContact(order.getShippingContactId().longValue()));
        }
        if (order.getInstallationContactId() != null) {
            order.setInstallationContact(
                    contactDAO.getContact(order.getInstallationContactId().longValue()));
        }
        if (order.getMaintenanceWindowId() != null) {
            order.setMaintenanceWindow(maintenanceWindowDAO.getMaintenanceWindowForOrder(orderId));
        }
        if (order.getConfigurationId() != null) {
            order.setAssetConfiguration(assetConfigDAO.getConfigurationForOrder(orderId));
        }
        if (order.getShippingWindowTimeslotId() != null) {
            order.setShippingWindow(shippingCalendarService
                    .getShippingWindow(order.getShippingWindowTimeslotId().longValue()));
        }
        order.setSubscriberPcs(subscriberPcDAO.getSubscriberPcs(orderId));
        return order;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public boolean cancelOrder(final long customerId, final long orderId, final String reason,
            final String userId) {
        final Order order = orderDAO.getOrder(customerId, orderId);
        if (order == null) {
            throw new IllegalArgumentException("No order " + orderId + " for customer " + customerId);
        }
        final boolean penalty = order.isCancellationPenaltyIncurred(getMinimumHoursToCancel());
        final int updated = orderDAO.cancelOrder(orderId, reason, penalty, order.getCurrentTime(),
                userId);
        if (updated == 0) {
            LOGGER.info("Order {} was already closed; nothing cancelled", Long.valueOf(orderId));
            return false;
        }
        requestDAO.recordEvent(EventType.ORDER_CANCELLED, EmailEntityType.ORDER, orderId,
                "Order cancelled" + (penalty ? " with penalty" : "") + ": " + reason, userId);
        storedProcedureDAO.addEntityEmail(EmailEntityType.ORDER.getCode(), orderId,
                EmailTemplateType.ORDER_CANCELLATION.getCode(), userId);
        return true;
    }

    @Override
    public boolean isCancellationPenaltyIncurred(final long customerId, final long orderId,
            final Date asOf) {
        final Order order = orderDAO.getOrder(customerId, orderId);
        if (order == null) {
            return false;
        }
        order.setCurrentTime(asOf);
        return order.isCancellationPenaltyIncurred(getMinimumHoursToCancel());
    }

    @Override
    public Date getEarliestInstallationDate(final Order order) {
        if (order == null) {
            return null;
        }
        final Date from = order.getSubmittedDate() == null
                ? order.getCurrentTime() : order.getSubmittedDate();
        return calendarService.addBusinessDays(from, order.getInstallLeadTimeDays());
    }

    private int getMinimumHoursToCancel() {
        return configService.getInt(
                PropertyType.MIN_HOURS_BEFORE_INSTALLATION_TO_CANCEL_ORDER_WITHOUT_PENALTY,
                DEFAULT_MIN_HOURS_TO_CANCEL);
    }
}
