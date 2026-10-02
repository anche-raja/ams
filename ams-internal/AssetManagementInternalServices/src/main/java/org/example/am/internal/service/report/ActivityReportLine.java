package org.example.am.internal.service.report;

import java.io.Serializable;
import java.util.Date;

/**
 * One customer's row on the order activity report: how many orders they have in each state, how
 * long the oldest open one has been waiting, and the most recent order placed for them.
 *
 * <p>A plain value holder. Every field has an accessor because the report JSP reads them through
 * OGNL and the CSV export reads them directly; equality is by customer id, which is what the
 * report is keyed on.</p>
 */
public class ActivityReportLine implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long customerId;
    private String customerName;
    private String accountNumber;
    private boolean orderingEnabled;
    private int openOrders;
    private int scheduledInstallations;
    private int completedOrders;
    private int cancelledOrders;
    /** Age in whole days of the oldest order still open, or {@code null} when nothing is open. */
    private Integer oldestOpenOrderAgeDays;
    private String latestOrderNumber;
    private Date latestSubmittedDate;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(final String customerName) {
        this.customerName = customerName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public boolean isOrderingEnabled() {
        return orderingEnabled;
    }

    public void setOrderingEnabled(final boolean orderingEnabled) {
        this.orderingEnabled = orderingEnabled;
    }

    public int getOpenOrders() {
        return openOrders;
    }

    public void setOpenOrders(final int openOrders) {
        this.openOrders = openOrders;
    }

    public int getScheduledInstallations() {
        return scheduledInstallations;
    }

    public void setScheduledInstallations(final int scheduledInstallations) {
        this.scheduledInstallations = scheduledInstallations;
    }

    public int getCompletedOrders() {
        return completedOrders;
    }

    public void setCompletedOrders(final int completedOrders) {
        this.completedOrders = completedOrders;
    }

    public int getCancelledOrders() {
        return cancelledOrders;
    }

    public void setCancelledOrders(final int cancelledOrders) {
        this.cancelledOrders = cancelledOrders;
    }

    public Integer getOldestOpenOrderAgeDays() {
        return oldestOpenOrderAgeDays;
    }

    public void setOldestOpenOrderAgeDays(final Integer oldestOpenOrderAgeDays) {
        this.oldestOpenOrderAgeDays = oldestOpenOrderAgeDays;
    }

    public String getLatestOrderNumber() {
        return latestOrderNumber;
    }

    public void setLatestOrderNumber(final String latestOrderNumber) {
        this.latestOrderNumber = latestOrderNumber;
    }

    public Date getLatestSubmittedDate() {
        return latestSubmittedDate == null ? null : new Date(latestSubmittedDate.getTime());
    }

    public void setLatestSubmittedDate(final Date latestSubmittedDate) {
        this.latestSubmittedDate =
                latestSubmittedDate == null ? null : new Date(latestSubmittedDate.getTime());
    }

    /** @return {@code true} when at least one order for this customer is still open */
    public boolean hasOpenOrders() {
        return openOrders > 0;
    }

    /** @return every order counted on this line, whatever its state */
    public int getTotalOrders() {
        return openOrders + completedOrders + cancelledOrders;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActivityReportLine)) {
            return false;
        }
        final ActivityReportLine that = (ActivityReportLine) other;
        if (customerId == null) {
            return that.customerId == null;
        }
        return customerId.equals(that.customerId);
    }

    @Override
    public int hashCode() {
        return customerId == null ? 0 : customerId.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder("ActivityReportLine[");
        text.append("customerId=").append(customerId);
        text.append(", customerName=").append(customerName);
        text.append(", open=").append(openOrders);
        text.append(", scheduled=").append(scheduledInstallations);
        text.append(", completed=").append(completedOrders);
        text.append(", cancelled=").append(cancelledOrders);
        text.append(", oldestOpenAgeDays=").append(oldestOpenOrderAgeDays);
        text.append(", latestOrder=").append(latestOrderNumber);
        text.append(']');
        return text.toString();
    }
}
