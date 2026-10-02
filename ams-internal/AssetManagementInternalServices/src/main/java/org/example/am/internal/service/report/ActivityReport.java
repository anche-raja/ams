package org.example.am.internal.service.report;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The order activity report: one line per customer, the totals behind them, and the moment it was
 * produced. Immutable once built, which is why the collections are copied and wrapped on the way
 * in rather than exposed as handed over.
 */
public class ActivityReport implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Date generatedAt;
    private final Date earliestInstallationDate;
    private final List<ActivityReportLine> lines;
    private final Map<String, Integer> ordersByStatus;
    private final String summary;

    public ActivityReport(final Date generatedAt, final Date earliestInstallationDate,
            final List<ActivityReportLine> lines, final Map<String, Integer> ordersByStatus,
            final String summary) {
        this.generatedAt = generatedAt == null ? new Date() : new Date(generatedAt.getTime());
        this.earliestInstallationDate = earliestInstallationDate == null
                ? null : new Date(earliestInstallationDate.getTime());
        this.lines = Collections.unmodifiableList(lines == null
                ? new ArrayList<ActivityReportLine>()
                : new ArrayList<ActivityReportLine>(lines));
        this.ordersByStatus = Collections.unmodifiableMap(ordersByStatus == null
                ? new LinkedHashMap<String, Integer>()
                : new LinkedHashMap<String, Integer>(ordersByStatus));
        this.summary = summary == null ? "" : summary;
    }

    /** @return when the report was produced; every age on it is measured from this moment */
    public Date getGeneratedAt() {
        return new Date(generatedAt.getTime());
    }

    /** @return the first day an order placed now could be installed, or {@code null} if unknown */
    public Date getEarliestInstallationDate() {
        return earliestInstallationDate == null ? null : new Date(earliestInstallationDate.getTime());
    }

    public List<ActivityReportLine> getLines() {
        return lines;
    }

    /** @return order counts keyed by status code, in the order the statuses were first seen */
    public Map<String, Integer> getOrdersByStatus() {
        return ordersByStatus;
    }

    public String getSummary() {
        return summary;
    }

    public int getCustomerCount() {
        return lines.size();
    }

    /** @return the number of customers with at least one order still open */
    public int getCustomersWithOpenOrders() {
        int count = 0;
        final Iterator<ActivityReportLine> iterator = lines.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().hasOpenOrders()) {
                count++;
            }
        }
        return count;
    }

    public int getTotalOpenOrders() {
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += lines.get(i).getOpenOrders();
        }
        return total;
    }

    public int getTotalOrders() {
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += lines.get(i).getTotalOrders();
        }
        return total;
    }
}
