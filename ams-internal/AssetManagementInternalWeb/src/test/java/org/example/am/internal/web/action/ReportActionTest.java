package org.example.am.internal.web.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.am.internal.service.OrderActivityService;
import org.example.am.internal.service.report.ActivityReport;
import org.example.am.internal.service.report.ActivityReportLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.opensymphony.xwork2.Action;

/**
 * The action adds two things to the service's report: the role check, and the CSV rendering. Both
 * are checked here against a canned report, so no database is involved.
 */
public class ReportActionTest {

    private ReportAction action;
    private MockHttpServletRequest request;
    private OrderActivityService orderActivityService;

    @BeforeEach
    public void setUp() {
        request = new MockHttpServletRequest();
        orderActivityService = mock(OrderActivityService.class);
        when(orderActivityService.buildReport(any(Date.class))).thenReturn(cannedReport());

        action = new ReportAction();
        action.setServletRequest(request);
        ReflectionTestUtils.setField(action, "orderActivityService", orderActivityService);
    }

    @Test
    public void theReportNeedsTheViewOrderRole() throws Exception {
        assertEquals(BaseAction.UNAUTHORIZED, action.report());
        assertNull(action.getReport());

        request.addUserRole("INT_VIEW_ORDER");
        assertEquals(Action.SUCCESS, action.report());
        assertNotNull(action.getReport());
        assertEquals(2, action.getReport().getCustomerCount());
    }

    @Test
    public void theExportNeedsTheSameRole() throws Exception {
        assertEquals(BaseAction.UNAUTHORIZED, action.exportCsv());
        assertNull(action.getCsvStream());
    }

    @Test
    public void theExportStreamsTheCsvWithADatedFileName() throws Exception {
        request.addUserRole("INT_VIEW_ORDER");
        assertEquals(Action.SUCCESS, action.exportCsv());

        final List<String> lines = readLines(action.getCsvStream());
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).startsWith("# 2 customers"));
        assertEquals("Customer,Account,Ordering,Open orders,Scheduled,Completed,Cancelled,"
                + "Oldest open (days),Latest order,Latest submitted", lines.get(1));
        assertEquals("order-activity-20260902.csv", action.getCsvFileName());
    }

    /** A comma or a quote inside a name must not move the columns along. */
    @Test
    public void textFieldsAreQuotedAndEmptyValuesStayEmpty() {
        final String csv = ReportAction.toCsv(cannedReport());
        final String[] rows = csv.split("\r\n");

        assertEquals("\"Lakeshore Legal, Partners\",\"LL-300\",Y,2,1,3,0,12,\"LL-9\",2026-08-30",
                rows[2]);
        assertEquals("\"Sundial \"\"Co-op\"\"\",,N,0,0,0,1,,,", rows[3]);
    }

    private static ActivityReport cannedReport() {
        final List<ActivityReportLine> lines = new ArrayList<ActivityReportLine>();

        final ActivityReportLine lakeshore = new ActivityReportLine();
        lakeshore.setCustomerId(Long.valueOf(1003L));
        lakeshore.setCustomerName("Lakeshore Legal, Partners");
        lakeshore.setAccountNumber("LL-300");
        lakeshore.setOrderingEnabled(true);
        lakeshore.setOpenOrders(2);
        lakeshore.setScheduledInstallations(1);
        lakeshore.setCompletedOrders(3);
        lakeshore.setOldestOpenOrderAgeDays(Integer.valueOf(12));
        lakeshore.setLatestOrderNumber("LL-9");
        lakeshore.setLatestSubmittedDate(at(2026, Calendar.AUGUST, 30));
        lines.add(lakeshore);

        final ActivityReportLine sundial = new ActivityReportLine();
        sundial.setCustomerId(Long.valueOf(1004L));
        sundial.setCustomerName("Sundial \"Co-op\"");
        sundial.setCancelledOrders(1);
        lines.add(sundial);

        final Map<String, Integer> byStatus = new LinkedHashMap<String, Integer>();
        byStatus.put("SUBMITTED", Integer.valueOf(1));
        byStatus.put("SCHEDULED", Integer.valueOf(1));

        return new ActivityReport(at(2026, Calendar.SEPTEMBER, 2), at(2026, Calendar.SEPTEMBER, 7),
                lines, byStatus, "2 customers, 2 open orders across 1 of them.");
    }

    private static List<String> readLines(final java.io.InputStream stream) throws IOException {
        final List<String> lines = new ArrayList<String>();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        try {
            String line = reader.readLine();
            while (line != null) {
                lines.add(line);
                line = reader.readLine();
            }
        } finally {
            reader.close();
        }
        return lines;
    }

    private static Date at(final int year, final int month, final int day) {
        final Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day);
        return calendar.getTime();
    }
}
