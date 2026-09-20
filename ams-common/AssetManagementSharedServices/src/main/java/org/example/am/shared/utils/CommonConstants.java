package org.example.am.shared.utils;

/**
 * Constants shared by the DAO and service layers. Kept as a final class of constants rather than an
 * interface so that nothing can inherit them by implementing it.
 */
public final class CommonConstants {

    /** Named parameter keys reused across the hand written SQL. */
    public static final String PARAM_ASSET_ID = "assetId";
    public static final String PARAM_ORDER_ID = "orderId";
    public static final String PARAM_CUSTOMER_ID = "customerId";
    public static final String PARAM_CONTACT_ID = "contactId";
    public static final String PARAM_ADDRESS_ID = "addressId";
    public static final String PARAM_NCR_ID = "networkChangeRequestId";
    public static final String PARAM_USER_ID = "userId";
    public static final String PARAM_STATUS_CODE = "statusCode";
    public static final String PARAM_TYPE_CODE = "typeCode";
    public static final String PARAM_START_DATE = "startDate";
    public static final String PARAM_END_DATE = "endDate";
    public static final String PARAM_MAX_ROWS = "maxRows";

    /** The internal search grid never returns more than this many rows. */
    public static final int MAX_SEARCH_RESULTS = 500;

    /** Oracle flag columns are single character. */
    public static final String YES = "Y";
    public static final String NO = "N";

    /** Sequences and packages referenced by name from the DAOs. */
    public static final String SEQ_ASSET_RETURNS = "AMS_ASSET_RETURNS_SQ";
    public static final String SEQ_ORDERS = "AMS_ORDERS_SQ";
    public static final String SEQ_EVENTS = "AMS_EVENTS_SQ";
    public static final String SEQ_SAVE_FOR_LATER = "AMS_SAVE_FOR_LATER_SQ";
    public static final String SEQ_SUBSCRIBER_PCS = "AMS_SUBSCRIBER_PCS_SQ";
    public static final String SEQ_MAINTENANCE_WINDOWS = "AMS_MAINT_WINDOWS_SQ";
    public static final String SEQ_ASSETS = "AMS_ASSETS_SQ";
    public static final String SEQ_INSTALLATIONS = "AMS_INSTALLATIONS_SQ";
    public static final String SEQ_EMAIL_QUEUE = "AMS_EMAIL_QUEUE_SQ";
    public static final String SEQ_DECOMMISSIONS = "AMS_DECOMMISSIONS_SQ";
    public static final String SEQ_TIMESLOT_RESERVATIONS = "AMS_TIMESLOT_RESERVATIONS_SQ";
    public static final String SEQ_CIRCUIT_WINDOWS = "AMS_CIRCUIT_WINDOWS_SQ";

    /** Spring profile names; the security wiring is gated on these. */
    public static final String PROFILE_LOCAL = "local";
    public static final String PROFILE_DEV = "dev";
    public static final String PROFILE_FIT = "fit";
    public static final String PROFILE_QA = "qa";
    public static final String PROFILE_PRODUCTION = "production";

    private CommonConstants() {
        super();
    }
}
