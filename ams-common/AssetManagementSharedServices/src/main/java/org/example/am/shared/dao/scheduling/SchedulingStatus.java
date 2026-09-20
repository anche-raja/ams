package org.example.am.shared.dao.scheduling;

/**
 * Every outcome the scheduling and notification operations can report.
 *
 * <p>These were the {@code C_*} constants in the three PL/SQL package specifications. They cross
 * the DAO boundary as plain strings and are compared with {@code equals} at every call site, so the
 * values here must stay byte-identical to the originals.</p>
 *
 * <p>Only {@link #OK} means the work was done. Everything else is an ordinary business outcome the
 * user interface reports - not an error, and not something to throw for. Most of these strings are
 * never tested by a caller: the call sites all ask {@code "OK".equals(status)} and log the rest, so
 * for every failure other than the common ones this string is the only record of <em>why</em>
 * something did not happen.</p>
 *
 * <p><strong>Lock ordering.</strong> The operations that take row locks take them in one fixed
 * order, and it is the reason the reschedule path cannot deadlock against a concurrent booking:</p>
 *
 * <pre>
 *   network change request / decommission
 *     -&gt; timeslot, ascending by TIMESLOT_ID
 *       -&gt; reservation ledger
 *         -&gt; installation
 *           -&gt; order
 *             -&gt; asset
 * </pre>
 */
public final class SchedulingStatus {

    /** The work was done. Nothing else means that. */
    public static final String OK = "OK";

    /** The slot filled up before this caller got to it. The common, expected race. */
    public static final String NO_CAPACITY = "NO_CAPACITY";

    /** Another session held the row longer than the lock wait allows. */
    public static final String BUSY = "BUSY";

    /** Cancelling a reservation the caller does not hold. Makes a double cancel harmless. */
    public static final String NOT_RESERVED = "NOT_RESERVED";

    /** Ops has closed the slot; distinct from it being full. */
    public static final String SLOT_CLOSED = "SLOT_CLOSED";

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID_INPUT = "INVALID_INPUT";
    public static final String NOT_OPEN = "NOT_OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String NOT_SCHEDULED = "NOT_SCHEDULED";
    public static final String ASSET_MISMATCH = "ASSET_MISMATCH";
    public static final String DATE_IN_PAST = "DATE_IN_PAST";
    public static final String OUTSIDE_WINDOW = "OUTSIDE_WINDOW";
    public static final String NO_INSTALLATION = "NO_INSTALLATION";
    public static final String NO_ASSET = "NO_ASSET";
    public static final String CUSTOMER_BLACKOUT = "CUSTOMER_BLACKOUT";
    public static final String INVALID_SITE_TYPE = "INVALID_SITE_TYPE";
    public static final String NO_CIRCUIT_WINDOW = "NO_CIRCUIT_WINDOW";
    public static final String CIRCUIT_WINDOW_CLOSED = "CIRCUIT_WINDOW_CLOSED";

    /** Notification outcomes. */
    public static final String INVALID_ENTITY_TYPE = "INVALID_ENTITY_TYPE";
    public static final String INVALID_TEMPLATE = "INVALID_TEMPLATE";
    public static final String NO_RECIPIENTS = "NO_RECIPIENTS";
    public static final String SUPPRESSED = "SUPPRESSED";

    /** Something unexpected. Only {@code addEntityEmail} ever returns this rather than throwing. */
    public static final String ERROR = "ERROR";

    private SchedulingStatus() {
        super();
    }
}
