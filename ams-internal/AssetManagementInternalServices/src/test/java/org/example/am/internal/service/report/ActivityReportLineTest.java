package org.example.am.internal.service.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

/** The line is a value object keyed on the customer, and its dates are copied on the way in and out. */
public class ActivityReportLineTest {

    private static ActivityReportLine line(final long customerId, final int open) {
        final ActivityReportLine line = new ActivityReportLine();
        line.setCustomerId(Long.valueOf(customerId));
        line.setCustomerName("Customer " + customerId);
        line.setOpenOrders(open);
        return line;
    }

    @Test
    public void linesForTheSameCustomerAreEqualWhateverTheirCounts() {
        assertEquals(line(1001L, 1), line(1001L, 7));
        assertEquals(line(1001L, 1).hashCode(), line(1001L, 7).hashCode());
    }

    @Test
    public void linesForDifferentCustomersAreNot() {
        assertFalse(line(1001L, 1).equals(line(1002L, 1)));
        assertFalse(line(1001L, 1).equals("1001"));
        assertFalse(line(1001L, 1).equals(null));
    }

    @Test
    public void totalsAndOpenFlagFollowTheCounts() {
        final ActivityReportLine line = line(1001L, 2);
        line.setCompletedOrders(3);
        line.setCancelledOrders(1);
        assertEquals(6, line.getTotalOrders());
        assertTrue(line.hasOpenOrders());
        assertFalse(line(1002L, 0).hasOpenOrders());
    }

    @Test
    public void theSubmittedDateIsDefensivelyCopied() {
        final Date original = new Date(1000L);
        final ActivityReportLine line = line(1001L, 0);
        line.setLatestSubmittedDate(original);
        original.setTime(2000L);
        assertEquals(1000L, line.getLatestSubmittedDate().getTime());
        assertNotSame(line.getLatestSubmittedDate(), line.getLatestSubmittedDate());
    }

    @Test
    public void toStringNamesTheCustomer() {
        assertTrue(line(1001L, 2).toString().contains("Customer 1001"));
    }
}
