package org.example.am.internal.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.service.OrderActivityService;
import org.example.am.internal.service.report.ActivityReport;
import org.example.am.internal.service.report.ActivityReportLine;
import org.example.am.shared.domain.Customer;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.service.CalendarService;
import org.example.am.shared.service.CustomerSearchService;
import org.example.am.shared.service.OrderService;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Walks every customer's orders once and folds them into the activity report.
 *
 * <p>The counting is driven by the order status code rather than by {@link Order#isOpen()},
 * because the report distinguishes states that method lumps together: an order whose installation
 * is scheduled is still open, but it is also the one state operations want to see on its own.</p>
 */
@Service("orderActivityService")
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class OrderActivityServiceImpl implements OrderActivityService {

    private static final Logger LOGGER = LogManager.getLogger(OrderActivityServiceImpl.class);

    /** How long after an order is placed its installation can first happen, in business days. */
    static final int INSTALLATION_LEAD_TIME_BUSINESS_DAYS = 3;

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private static final String SUMMARY_DATE_FORMAT = "d MMM yyyy HH:mm";

    @Autowired
    private CustomerSearchService customerSearchService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CalendarService calendarService;

    @Override
    public ActivityReport buildReport(final Date asOf) {
        final Date reportDate = asOf == null ? new Date() : asOf;

        final List<Customer> customers =
                customerSearchService.listAll(CommonConstants.MAX_SEARCH_RESULTS);
        final List<ActivityReportLine> lines = new ArrayList<ActivityReportLine>();
        final Map<String, Integer> ordersByStatus = new LinkedHashMap<String, Integer>();

        for (int i = 0; i < customers.size(); i++) {
            final Customer customer = customers.get(i);
            if (customer == null || customer.getCustomerId() == null) {
                continue;
            }
            final List<Order> orders =
                    orderService.getOrdersForCustomer(customer.getCustomerId().longValue());
            lines.add(summarise(customer, orders, reportDate, ordersByStatus));
        }

        // Most open orders first; equal counts fall back to the customer name.
        Collections.sort(lines, new Comparator<ActivityReportLine>() {
            @Override
            public int compare(final ActivityReportLine left, final ActivityReportLine right) {
                if (left.getOpenOrders() != right.getOpenOrders()) {
                    return right.getOpenOrders() - left.getOpenOrders();
                }
                return String.valueOf(left.getCustomerName())
                        .compareToIgnoreCase(String.valueOf(right.getCustomerName()));
            }
        });

        final Date earliestInstallation =
                calendarService.addBusinessDays(reportDate, INSTALLATION_LEAD_TIME_BUSINESS_DAYS);

        LOGGER.debug("Order activity report built for {} customers", Integer.valueOf(lines.size()));
        return new ActivityReport(reportDate, earliestInstallation, lines, ordersByStatus,
                describe(lines, ordersByStatus, reportDate));
    }

    private ActivityReportLine summarise(final Customer customer, final List<Order> orders,
            final Date asOf, final Map<String, Integer> ordersByStatus) {
        final ActivityReportLine line = new ActivityReportLine();
        line.setCustomerId(customer.getCustomerId());
        line.setCustomerName(customer.getCustomerName());
        line.setAccountNumber(customer.getAccountNumber());
        line.setOrderingEnabled(customer.isCanSubmitOrders());

        int open = 0;
        int scheduled = 0;
        int completed = 0;
        int cancelled = 0;
        Date oldestOpen = null;
        Order latest = null;

        final Iterator<Order> iterator = orders == null
                ? Collections.<Order>emptyList().iterator() : orders.iterator();
        while (iterator.hasNext()) {
            final Order order = iterator.next();
            final OrderStatusType status = order.getOrderStatusType();
            final String code = status == null ? "" : status.getCode();
            count(ordersByStatus, code);

            switch (code) {
                case "SAVED":
                case "SUBMITTED":
                case "FULFILL":
                case "SHIPPED":
                case "ONHOLD":
                    open++;
                    oldestOpen = earlier(oldestOpen, order.getSubmittedDate());
                    break;
                case "SCHEDULED":
                    open++;
                    scheduled++;
                    oldestOpen = earlier(oldestOpen, order.getSubmittedDate());
                    break;
                case "INSTALLED":
                case "COMPLETED":
                    completed++;
                    break;
                case "CANCELLED":
                    cancelled++;
                    break;
                default:
                    LOGGER.warn("Order {} for customer {} has an unrecognised status '{}'",
                            order.getOrderNumber(), customer.getCustomerId(), code);
                    break;
            }

            if (latest == null || isAfter(order.getSubmittedDate(), latest.getSubmittedDate())) {
                latest = order;
            }
        }

        line.setOpenOrders(open);
        line.setScheduledInstallations(scheduled);
        line.setCompletedOrders(completed);
        line.setCancelledOrders(cancelled);
        if (oldestOpen != null) {
            line.setOldestOpenOrderAgeDays(Integer.valueOf(daysBetween(oldestOpen, asOf)));
        }
        if (latest != null) {
            line.setLatestOrderNumber(latest.getOrderNumber());
            line.setLatestSubmittedDate(latest.getSubmittedDate());
        }
        return line;
    }

    private static void count(final Map<String, Integer> totals, final String code) {
        if (totals.containsKey(code)) {
            totals.put(code, Integer.valueOf(totals.get(code).intValue() + 1));
        } else {
            totals.put(code, Integer.valueOf(1));
        }
    }

    /** @return the earlier of the two, treating {@code null} as "no date" */
    private static Date earlier(final Date current, final Date candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.before(current)) {
            return candidate;
        }
        return current;
    }

    private static boolean isAfter(final Date candidate, final Date current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.after(current);
    }

    /**
     * Whole calendar days from one date to another, ignoring the time of day on each. Rounded
     * rather than truncated so a daylight-saving change in between does not lose a day.
     */
    static int daysBetween(final Date from, final Date to) {
        final Calendar start = Calendar.getInstance();
        start.setTime(from);
        clearTime(start);
        final Calendar end = Calendar.getInstance();
        end.setTime(to);
        clearTime(end);
        final long millis = end.getTimeInMillis() - start.getTimeInMillis();
        return (int) Math.round((double) millis / MILLIS_PER_DAY);
    }

    private static void clearTime(final Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    /** The one-line summary shown above the table and written at the top of the CSV. */
    private static String describe(final List<ActivityReportLine> lines,
            final Map<String, Integer> ordersByStatus, final Date asOf) {
        int customersWithOpen = 0;
        int open = 0;
        for (int i = 0; i < lines.size(); i++) {
            final ActivityReportLine line = lines.get(i);
            if (line.hasOpenOrders()) {
                customersWithOpen++;
            }
            open += line.getOpenOrders();
        }

        final StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.US, "%d customer%s, %d open order%s across %d of them",
                Integer.valueOf(lines.size()), lines.size() == 1 ? "" : "s",
                Integer.valueOf(open), open == 1 ? "" : "s",
                Integer.valueOf(customersWithOpen)));

        if (!ordersByStatus.isEmpty()) {
            text.append(" (");
            final Iterator<Map.Entry<String, Integer>> entries = ordersByStatus.entrySet().iterator();
            boolean first = true;
            while (entries.hasNext()) {
                final Map.Entry<String, Integer> entry = entries.next();
                if (!first) {
                    text.append(", ");
                }
                first = false;
                final OrderStatusType status = OrderStatusType.lookup(entry.getKey());
                text.append(status == null ? entry.getKey() : status.getDescription());
                text.append(' ').append(entry.getValue());
            }
            text.append(')');
        }

        // SimpleDateFormat is not thread safe, so it is created where it is used and not shared.
        final SimpleDateFormat format = new SimpleDateFormat(SUMMARY_DATE_FORMAT, Locale.US);
        text.append(", as of ").append(format.format(asOf)).append('.');
        return text.toString();
    }
}
