package org.example.am.internal.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.example.am.internal.service.report.ActivityReport;
import org.example.am.internal.service.report.ActivityReportLine;
import org.example.am.shared.domain.Customer;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.service.CalendarService;
import org.example.am.shared.service.CustomerSearchService;
import org.example.am.shared.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The report is a fold over every customer's orders, so the cases that matter are the counting
 * rules: which statuses count as open, how the oldest open order is aged, and the sort order.
 */
@ExtendWith(MockitoExtension.class)
public class OrderActivityServiceImplTest {

    private static final long NORTHWIND = 1001L;
    private static final long BEACON = 1002L;

    private static final Date AS_OF = at(2026, Calendar.OCTOBER, 2);

    @Mock
    private CustomerSearchService customerSearchService;

    @Mock
    private OrderService orderService;

    @Mock
    private CalendarService calendarService;

    @InjectMocks
    private OrderActivityServiceImpl service;

    @BeforeEach
    public void setUp() {
        when(customerSearchService.listAll(anyInt())).thenReturn(Arrays.asList(
                customer(NORTHWIND, "Northwind Coffee Roasters", "NW-100", true),
                customer(BEACON, "Beacon Hill Physio", "BH-200", false)));
        when(orderService.getOrdersForCustomer(NORTHWIND)).thenReturn(Arrays.asList(
                order("NW-1", OrderStatusType.SUBMITTED, daysBefore(AS_OF, 5)),
                order("NW-2", OrderStatusType.COMPLETED, daysBefore(AS_OF, 40)),
                order("NW-3", OrderStatusType.SCHEDULED, daysBefore(AS_OF, 2)),
                order("NW-4", OrderStatusType.CANCELLED, daysBefore(AS_OF, 10))));
        when(orderService.getOrdersForCustomer(BEACON))
                .thenReturn(Collections.<Order>emptyList());
        when(calendarService.addBusinessDays(any(Date.class), anyInt()))
                .thenReturn(at(2026, Calendar.OCTOBER, 7));
    }

    @Test
    public void ordersAreCountedByState() {
        final ActivityReportLine northwind = line(service.buildReport(AS_OF), NORTHWIND);

        assertEquals(2, northwind.getOpenOrders());
        assertEquals(1, northwind.getScheduledInstallations());
        assertEquals(1, northwind.getCompletedOrders());
        assertEquals(1, northwind.getCancelledOrders());
        assertEquals(4, northwind.getTotalOrders());
        assertTrue(northwind.isOrderingEnabled());
    }

    @Test
    public void theOldestOpenOrderIsAgedInWholeDays() {
        final ActivityReport report = service.buildReport(AS_OF);

        // NW-1 (5 days) is older than NW-3 (2 days); the completed and cancelled ones do not count.
        assertEquals(Integer.valueOf(5), line(report, NORTHWIND).getOldestOpenOrderAgeDays());
        assertNull("nothing open, so no age", line(report, BEACON).getOldestOpenOrderAgeDays());
    }

    @Test
    public void theLatestOrderIsTheMostRecentlySubmittedWhateverItsState() {
        final ActivityReportLine northwind = line(service.buildReport(AS_OF), NORTHWIND);
        assertEquals("NW-3", northwind.getLatestOrderNumber());
        assertEquals(daysBefore(AS_OF, 2), northwind.getLatestSubmittedDate());
    }

    @Test
    public void customersWithTheMostOpenOrdersComeFirst() {
        final List<ActivityReportLine> lines = service.buildReport(AS_OF).getLines();
        assertEquals(Long.valueOf(NORTHWIND), lines.get(0).getCustomerId());
        assertEquals(Long.valueOf(BEACON), lines.get(1).getCustomerId());
    }

    @Test
    public void totalsAndSummaryDescribeTheWholeReport() {
        final ActivityReport report = service.buildReport(AS_OF);

        assertEquals(2, report.getCustomerCount());
        assertEquals(1, report.getCustomersWithOpenOrders());
        assertEquals(2, report.getTotalOpenOrders());
        assertEquals(4, report.getTotalOrders());
        assertEquals(Integer.valueOf(1), report.getOrdersByStatus().get("SUBMITTED"));
        assertEquals(Integer.valueOf(1), report.getOrdersByStatus().get("SCHEDULED"));
        assertEquals(at(2026, Calendar.OCTOBER, 7), report.getEarliestInstallationDate());

        final String summary = report.getSummary();
        assertTrue(summary, summary.startsWith("2 customers, 2 open orders across 1 of them"));
        assertTrue(summary, summary.contains("Installation Scheduled 1"));
        assertTrue(summary, summary.endsWith("as of 2 Oct 2026 00:00."));
    }

    /** An order with no status is counted in the totals but is neither open nor closed. */
    @Test
    public void anOrderWithoutAStatusIsNotCountedAsOpen() {
        when(orderService.getOrdersForCustomer(BEACON)).thenReturn(
                Collections.singletonList(order("BH-1", null, daysBefore(AS_OF, 1))));

        final ActivityReportLine beacon = line(service.buildReport(AS_OF), BEACON);

        assertEquals(0, beacon.getOpenOrders());
        assertEquals(0, beacon.getTotalOrders());
        assertEquals("BH-1", beacon.getLatestOrderNumber());
        assertFalse(beacon.hasOpenOrders());
    }

    @Test
    public void aMissingAsOfMeansNow() {
        final Date before = new Date();
        final Date generated = service.buildReport(null).getGeneratedAt();
        assertFalse(generated.before(before));
    }

    @Test
    public void daysBetweenIgnoresTheTimeOfDay() {
        final Calendar lateEvening = Calendar.getInstance();
        lateEvening.setTime(at(2026, Calendar.MARCH, 1));
        lateEvening.set(Calendar.HOUR_OF_DAY, 23);
        final Calendar earlyMorning = Calendar.getInstance();
        earlyMorning.setTime(at(2026, Calendar.MARCH, 4));
        earlyMorning.set(Calendar.HOUR_OF_DAY, 1);

        assertEquals(3, OrderActivityServiceImpl.daysBetween(lateEvening.getTime(),
                earlyMorning.getTime()));
    }

    private static ActivityReportLine line(final ActivityReport report, final long customerId) {
        for (final ActivityReportLine line : report.getLines()) {
            if (line.getCustomerId().longValue() == customerId) {
                return line;
            }
        }
        throw new AssertionError("no line for customer " + customerId);
    }

    private static Customer customer(final long id, final String name, final String account,
            final boolean canOrder) {
        final Customer customer = new Customer();
        customer.setCustomerId(Long.valueOf(id));
        customer.setCustomerName(name);
        customer.setAccountNumber(account);
        customer.setCanSubmitOrders(canOrder);
        return customer;
    }

    private static Order order(final String number, final OrderStatusType status,
            final Date submitted) {
        final Order order = new Order();
        order.setOrderNumber(number);
        order.setOrderStatusType(status);
        order.setSubmittedDate(submitted);
        return order;
    }

    private static Date at(final int year, final int month, final int day) {
        final Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day);
        return calendar.getTime();
    }

    private static Date daysBefore(final Date date, final int days) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, -days);
        return calendar.getTime();
    }
}
