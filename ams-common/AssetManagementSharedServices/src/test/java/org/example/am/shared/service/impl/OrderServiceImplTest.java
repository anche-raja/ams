package org.example.am.shared.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.example.am.shared.dao.AddressDAO;
import org.example.am.shared.dao.AssetConfigDAO;
import org.example.am.shared.dao.ContactDAO;
import org.example.am.shared.dao.InstallationDAO;
import org.example.am.shared.dao.OrderDAO;
import org.example.am.shared.dao.RequestDAO;
import org.example.am.shared.dao.StoredProcedureDAO;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.AssetConfiguration;
import org.example.am.shared.domain.Contact;
import org.example.am.shared.domain.ContactType;
import org.example.am.shared.domain.EmailEntityType;
import org.example.am.shared.domain.EventType;
import org.example.am.shared.domain.Installation;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.domain.OrderType;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.service.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

/**
 * Unit test rather than an integration test: the point is what placing an install order writes and
 * how it behaves when the appointment is lost, not the SQL.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    private static final long CUSTOMER_ID = 1001L;
    private static final long NEW_ORDER_ID = 7777L;
    private static final long SLOT_ID = 9705L;
    private static final String USER = "junit";

    @Mock
    private OrderDAO orderDAO;

    @Mock
    private RequestDAO requestDAO;

    @Mock
    private StoredProcedureDAO storedProcedureDAO;

    @Mock
    private CalendarService calendarService;

    @Mock
    private AddressDAO addressDAO;

    @Mock
    private ContactDAO contactDAO;

    @Mock
    private AssetConfigDAO assetConfigDAO;

    @Mock
    private InstallationDAO installationDAO;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;

    @BeforeEach
    public void setUp() {
        order = new Order();
        order.setOrderNumber("ORD-TEST");
        order.setOrderType(OrderType.NEW_INSTALL);
        order.setCustomerId(Long.valueOf(CUSTOMER_ID));
        order.setDeviceNickname("Front desk router");
        order.setShippingAddress(new Address());

        final Contact contact = new Contact();
        contact.setFirstName("Pat");
        contact.setLastName("Lee");
        order.setInstallationContact(contact);
        order.setAssetConfiguration(new AssetConfiguration());

        final Timeslot slot = new Timeslot();
        slot.setTimeslotId(Long.valueOf(SLOT_ID));
        slot.setStartTime(new Date());
        final Installation installation = new Installation();
        installation.setTimeslot(slot);
        order.setInstallation(installation);

        when(orderDAO.insertOrder(order, USER)).thenReturn(Long.valueOf(NEW_ORDER_ID));
    }

    @Test
    public void placingWritesEverythingTheFlowCollected() {
        when(storedProcedureDAO.reserveTimeslot(anyLong(), anyLong(), anyString(),
                any(Date.class), anyString())).thenReturn("OK");

        assertEquals(NEW_ORDER_ID, orderService.placeInstallOrder(order, USER));

        assertEquals(OrderStatusType.SUBMITTED, order.getOrderStatusType());
        verify(addressDAO).insertAddress(any(Address.class), eq(USER));
        verify(contactDAO).insertContact(any(Contact.class), eq(USER));
        assertEquals(ContactType.INSTALLATION, order.getInstallationContact().getContactType());
        verify(assetConfigDAO).insertOrderConfiguration(any(AssetConfiguration.class),
                eq(NEW_ORDER_ID), eq(USER));
        verify(installationDAO).insertInstallation(eq(NEW_ORDER_ID), eq((Long) null),
                any(Long.class), any(Long.class), eq(USER));
        verify(requestDAO).recordEvent(eq(EventType.ORDER_SUBMITTED), eq(EmailEntityType.ORDER),
                eq(NEW_ORDER_ID), anyString(), eq(USER));
        verify(storedProcedureDAO).addEntityEmail(eq("ORDER"), eq(NEW_ORDER_ID), eq("ORDCONF"),
                eq(USER));
    }

    /**
     * The order row carries the address and contact ids, and those ids only exist once the rows
     * are written - so they have to reach the order after the inserts, not before.
     */
    @Test
    public void theOrderIsLinkedToTheSiteAddressAndContactItWrote() {
        doAnswer(new Answer<Long>() {
            @Override
            public Long answer(final InvocationOnMock invocation) {
                ((Address) invocation.getArguments()[0]).setAddressId(Long.valueOf(501L));
                return Long.valueOf(501L);
            }
        }).when(addressDAO).insertAddress(any(Address.class), anyString());
        doAnswer(new Answer<Long>() {
            @Override
            public Long answer(final InvocationOnMock invocation) {
                ((Contact) invocation.getArguments()[0]).setContactId(Long.valueOf(601L));
                return Long.valueOf(601L);
            }
        }).when(contactDAO).insertContact(any(Contact.class), anyString());

        orderService.placeInstallOrder(order, USER);

        assertEquals(Long.valueOf(501L), order.getShipAddressId());
        assertEquals(Long.valueOf(601L), order.getInstallationContactId());
        verify(installationDAO).insertInstallation(eq(NEW_ORDER_ID), eq((Long) null),
                eq(Long.valueOf(501L)), eq(Long.valueOf(601L)), eq(USER));
    }

    /** For INSTALL the scheduling port keys the reservation on the order, not the installation. */
    @Test
    public void theAppointmentIsReservedAgainstTheOrder() {
        when(storedProcedureDAO.reserveTimeslot(eq(SLOT_ID), eq(NEW_ORDER_ID), eq("INSTALL"),
                any(Date.class), eq(USER))).thenReturn("OK");

        orderService.placeInstallOrder(order, USER);

        assertTrue(order.isInstallationScheduled());
        assertEquals(order.getInstallation().getTimeslot().getStartTime(),
                order.getRequestedInstallationDate());
    }

    /**
     * The slot can fill between choosing it and pressing the button. The order must still be
     * placed, but it must not claim an appointment the reservation ledger does not back.
     */
    @Test
    public void losingTheAppointmentStillPlacesTheOrderUnscheduled() {
        when(storedProcedureDAO.reserveTimeslot(anyLong(), anyLong(), anyString(),
                any(Date.class), anyString())).thenReturn("NO_CAPACITY");

        assertEquals(NEW_ORDER_ID, orderService.placeInstallOrder(order, USER));

        assertFalse(order.isInstallationScheduled());
        verify(storedProcedureDAO).addEntityEmail(anyString(), eq(NEW_ORDER_ID), anyString(),
                eq(USER));
    }

    @Test
    public void noAppointmentChosenMeansNoReservation() {
        order.getInstallation().setTimeslot(null);

        orderService.placeInstallOrder(order, USER);

        verify(storedProcedureDAO, never()).reserveTimeslot(anyLong(), anyLong(), anyString(),
                any(Date.class), anyString());
        assertNull(order.getRequestedInstallationDate());
    }

    /** A contact that already has an id is on file; writing it again would fork the record. */
    @Test
    public void anExistingContactIsNotWrittenAgain() {
        order.getInstallationContact().setContactId(Long.valueOf(4242L));

        orderService.placeInstallOrder(order, USER);

        verify(contactDAO, never()).insertContact(any(Contact.class), anyString());
    }

    @Test
    public void theDetailReadHydratesTheInstallationTimeslot() {
        final Order stored = new Order();
        stored.setOrderId(Long.valueOf(NEW_ORDER_ID));
        when(orderDAO.getOrder(CUSTOMER_ID, NEW_ORDER_ID)).thenReturn(stored);
        final Installation installation = new Installation();
        final Timeslot idOnly = new Timeslot();
        idOnly.setTimeslotId(Long.valueOf(SLOT_ID));
        installation.setTimeslot(idOnly);
        when(installationDAO.getInstallationForOrder(NEW_ORDER_ID)).thenReturn(installation);
        final Timeslot full = new Timeslot();
        full.setTimeslotId(Long.valueOf(SLOT_ID));
        full.setDisplayLabel("Morning visit");
        when(calendarService.getTimeslot(SLOT_ID)).thenReturn(full);

        final Order detail = orderService.getOrderDetail(CUSTOMER_ID, NEW_ORDER_ID);

        assertEquals("Morning visit", detail.getInstallation().getTimeslot().getDisplayLabel());
    }

    @Test
    public void anOrderForAnotherCustomerIsNotFound() {
        assertNull(orderService.getOrderDetail(CUSTOMER_ID, 123L));
    }
}
