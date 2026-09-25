package org.example.am.shared.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.dao.AddressDAO;
import org.example.am.shared.dao.AssetConfigDAO;
import org.example.am.shared.dao.ContactDAO;
import org.example.am.shared.dao.InstallationDAO;
import org.example.am.shared.dao.OrderDAO;
import org.example.am.shared.dao.RequestDAO;
import org.example.am.shared.dao.StoredProcedureDAO;
import org.example.am.shared.dao.scheduling.SchedulingStatus;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.AssetConfiguration;
import org.example.am.shared.domain.Contact;
import org.example.am.shared.domain.ContactType;
import org.example.am.shared.domain.EmailEntityType;
import org.example.am.shared.domain.EmailTemplateType;
import org.example.am.shared.domain.EventType;
import org.example.am.shared.domain.FacilitationCallType;
import org.example.am.shared.domain.Installation;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.service.CalendarService;
import org.example.am.shared.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("orderService")
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LogManager.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderDAO orderDAO;

    @Autowired
    private RequestDAO requestDAO;

    @Autowired
    private StoredProcedureDAO storedProcedureDAO;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private AddressDAO addressDAO;

    @Autowired
    private ContactDAO contactDAO;

    @Autowired
    private AssetConfigDAO assetConfigDAO;

    @Autowired
    private InstallationDAO installationDAO;

    @Override
    public List<Order> getOrdersForCustomer(final long customerId) {
        return orderDAO.getOrdersForCustomer(customerId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public long placeInstallOrder(final Order order, final String userId) {
        order.setOrderStatusType(OrderStatusType.SUBMITTED);
        if (order.getSubmittedDate() == null) {
            order.setSubmittedDate(order.getCurrentTime());
        }
        final Timeslot slot = order.getInstallation() == null
                ? null : order.getInstallation().getTimeslot();
        if (slot != null) {
            order.setRequestedInstallationDate(slot.getStartTime());
        }

        // The address and the contact are written first because the order row carries their ids.
        // Everything else waits for the order, because it carries the order's.
        persistSiteAddress(order, userId);
        persistSiteContact(order, userId);

        final long orderId = orderDAO.insertOrder(order, userId);
        final Long configurationId = persistConfiguration(order, orderId, userId);
        orderDAO.linkOrderArtifacts(orderId, null, configurationId, null, userId);

        installationDAO.insertInstallation(orderId, null,
                order.getShippingAddress() == null ? null : order.getShippingAddress().getAddressId(),
                order.getInstallationContact() == null
                        ? null : order.getInstallationContact().getContactId(),
                userId);
        reserveInstallation(order, orderId, slot, userId);

        requestDAO.recordEvent(EventType.ORDER_SUBMITTED, EmailEntityType.ORDER, orderId,
                "Install order " + order.getOrderNumber() + " placed", userId);
        // Queued, not sent: the poller delivers it after this transaction commits, so a mail
        // failure can never roll the order back.
        storedProcedureDAO.addEntityEmail(EmailEntityType.ORDER.getCode(), orderId,
                EmailTemplateType.ORDER_CONFIRMATION.getCode(), userId);
        return orderId;
    }

    /**
     * A new row every time rather than reusing one: the address is a record of where this order
     * was installed, and editing it later would rewrite the history of an order already placed.
     */
    private void persistSiteAddress(final Order order, final String userId) {
        final Address address = order.getShippingAddress();
        if (address != null && address.getAddressId() == null) {
            addressDAO.insertAddress(address, userId);
            // Set again now it has an id: the setter is what copies it to SHIP_ADDRESS_ID.
            order.setShippingAddress(address);
        }
    }

    /** The one person at the site: the engineer's contact on the day, and who the order is for. */
    private void persistSiteContact(final Order order, final String userId) {
        final Contact contact = order.getInstallationContact();
        if (contact == null || contact.getContactId() != null) {
            return;
        }
        contact.setContactType(ContactType.INSTALLATION);
        contact.setCustomerId(order.getCustomerId());
        contact.setActive(true);
        contactDAO.insertContact(contact, userId);
        // Set again now it has an id: the setter is what copies it to INSTALL_CONTACT_ID.
        order.setInstallationContact(contact);
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
     * Takes a place on the chosen installation slot.
     *
     * <p>For {@code INSTALL} the scheduling port moves both the installation and the order to
     * {@code SCHEDULED} under the same lock that takes the place. When the slot filled in the
     * meantime nothing is thrown: throwing would discard everything the user keyed because an
     * engineer's morning filled while they were typing. The order stands unscheduled, and the
     * timeslot is cleared on the order passed in so the caller can say so.</p>
     */
    private void reserveInstallation(final Order order, final long orderId, final Timeslot slot,
            final String userId) {
        if (slot == null || slot.getTimeslotId() == null) {
            return;
        }
        final String status = storedProcedureDAO.reserveTimeslot(slot.getTimeslotId().longValue(),
                orderId, FacilitationCallType.INSTALLATION.getCode(), slot.getStartTime(), userId);
        if (!SchedulingStatus.OK.equals(status)) {
            LOGGER.info("Installation slot {} could not be reserved for order {}: {}",
                    slot.getTimeslotId(), Long.valueOf(orderId), status);
            order.getInstallation().setTimeslot(null);
        }
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
        if (order.getInstallationContactId() != null) {
            order.setInstallationContact(
                    contactDAO.getContact(order.getInstallationContactId().longValue()));
        }
        if (order.getConfigurationId() != null) {
            order.setAssetConfiguration(assetConfigDAO.getConfigurationForOrder(orderId));
        }
        final Installation installation = installationDAO.getInstallationForOrder(orderId);
        if (installation != null && installation.getTimeslot() != null) {
            installation.setTimeslot(calendarService.getTimeslot(
                    installation.getTimeslot().getTimeslotId().longValue()));
        }
        order.setInstallation(installation);
        return order;
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
}
