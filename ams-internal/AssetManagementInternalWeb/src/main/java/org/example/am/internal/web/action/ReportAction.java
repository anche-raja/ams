package org.example.am.internal.web.action;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.service.OrderActivityService;
import org.example.am.internal.service.report.ActivityReport;
import org.example.am.internal.service.report.ActivityReportLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * The order activity report: one row per customer, and the same rows as a CSV download.
 *
 * <p>Both methods build the report afresh. It is a handful of queries over a few hundred rows, and
 * a report that could be stale by the time it is downloaded is worse than one that costs a second
 * to produce.</p>
 */
@Component("ReportAction")
@Scope("prototype")
public class ReportAction extends BaseAction {

    private static final long serialVersionUID = 1L;

    private static final String CSV_DATE_FORMAT = "yyyy-MM-dd";
    private static final String CSV_FILE_DATE_FORMAT = "yyyyMMdd";
    private static final String CSV_LINE_END = "\r\n";

    private static final String CSV_HEADER = "Customer,Account,Ordering,Open orders,Scheduled,"
            + "Completed,Cancelled,Oldest open (days),Latest order,Latest submitted";

    @Autowired
    private transient OrderActivityService orderActivityService;

    private ActivityReport report;
    private transient InputStream csvStream;

    public String report() throws Exception {
        final String denied = requireRole(SecurityRoleType.INT_VIEW_ORDER);
        if (denied != null) {
            return denied;
        }
        report = orderActivityService.buildReport(getCurrentTime());
        return Action.SUCCESS;
    }

    /**
     * The same report as a file. A GET, because it changes nothing; the stream result in
     * {@code struts-report.xml} sends {@link #getCsvStream()} with the file name below.
     */
    public String exportCsv() throws Exception {
        final String denied = requireRole(SecurityRoleType.INT_VIEW_ORDER);
        if (denied != null) {
            return denied;
        }
        report = orderActivityService.buildReport(getCurrentTime());
        final String csv = toCsv(report);
        try {
            csvStream = new ByteArrayInputStream(csv.getBytes("UTF-8"));
        } catch (final UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is not supported by this JVM", impossible);
        }
        logger.info("User {} exported the order activity report for {} customers", getUserId(),
                Integer.valueOf(report.getCustomerCount()));
        return Action.SUCCESS;
    }

    /**
     * Renders the report as CSV: the summary as a comment line, a header, then a row per customer.
     * RFC 4180 quoting - every text field is quoted and embedded quotes are doubled - so a customer
     * name with a comma in it does not shift the columns.
     */
    static String toCsv(final ActivityReport report) {
        final SimpleDateFormat dates = new SimpleDateFormat(CSV_DATE_FORMAT, Locale.US);
        final StringBuffer csv = new StringBuffer();
        csv.append("# ").append(report.getSummary()).append(CSV_LINE_END);
        csv.append(CSV_HEADER).append(CSV_LINE_END);

        final List<ActivityReportLine> lines = report.getLines();
        for (int i = 0; i < lines.size(); i++) {
            final ActivityReportLine line = lines.get(i);
            csv.append(quote(line.getCustomerName())).append(',');
            csv.append(quote(line.getAccountNumber())).append(',');
            csv.append(line.isOrderingEnabled() ? "Y" : "N").append(',');
            csv.append(line.getOpenOrders()).append(',');
            csv.append(line.getScheduledInstallations()).append(',');
            csv.append(line.getCompletedOrders()).append(',');
            csv.append(line.getCancelledOrders()).append(',');
            if (line.getOldestOpenOrderAgeDays() != null) {
                csv.append(line.getOldestOpenOrderAgeDays().intValue());
            }
            csv.append(',');
            csv.append(quote(line.getLatestOrderNumber())).append(',');
            if (line.getLatestSubmittedDate() != null) {
                csv.append(dates.format(line.getLatestSubmittedDate()));
            }
            csv.append(CSV_LINE_END);
        }
        return csv.toString();
    }

    private static String quote(final String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public ActivityReport getReport() {
        return report;
    }

    /** Read by the stream result as the response body. */
    public InputStream getCsvStream() {
        return csvStream;
    }

    /** Read by the stream result for the Content-Disposition file name. */
    public String getCsvFileName() {
        final SimpleDateFormat format = new SimpleDateFormat(CSV_FILE_DATE_FORMAT, Locale.US);
        return "order-activity-" + format.format(report == null ? getCurrentTime()
                : report.getGeneratedAt()) + ".csv";
    }
}
