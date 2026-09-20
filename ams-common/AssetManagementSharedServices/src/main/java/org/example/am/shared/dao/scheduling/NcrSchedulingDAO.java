package org.example.am.shared.dao.scheduling;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Books and releases network change dates, and the circuit windows a site-type change needs.
 *
 * <p>Ported from {@code AMS_NCR_SCHEDULING_PG}. The package body is kept under
 * {@code db/oracle/06_packages} as the original specification.</p>
 *
 * <p>Two properties are load-bearing and easy to lose:</p>
 *
 * <ul>
 *   <li><strong>The request is locked before any timeslot row, always.</strong> That fixed order is
 *       what stops {@code RescheduleNcrAction}'s cancel-then-reserve deadlocking against a
 *       concurrent booking, and the two release loops both iterate ascending by
 *       {@code TIMESLOT_ID} for the same reason.</li>
 *   <li><strong>Cancelling releases the date, never the request.</strong> The status goes
 *       {@code SCHEDULED -> SUBMITTED} and never to {@code CANCELLED}; cancelling the request
 *       itself is the service layer's job. If this regresses, the reschedule flow destroys live
 *       requests every time somebody moves a date.</li>
 * </ul>
 */
@Repository("ncrSchedulingDAO")
public class NcrSchedulingDAO extends BaseDAO {

    /** Used when an asset's address does not map to a region. */
    private static final String FALLBACK_REGION = "ALL";

    private static final Set<String> SITE_TYPES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("LANA", "LANB", "LANC", "WAN", "DUALWAN", "HAPAIR")));

    private static final String LOCK_REQUEST =
            "SELECT NCR_STATUS_CD, CUSTOMER_ID, ASSET_ID, SCHEDULED_DT "
          + "  FROM AMS_NETWORK_CHANGE_REQUESTS WHERE NCR_ID = :ncrId FOR UPDATE ";

    private static final String COUNT_BLACKOUTS =
            "SELECT COUNT(*) FROM AMS_CUSTOMER_SCHEDULES "
          + " WHERE CUSTOMER_ID = :customerId AND TRUNC(BLACKOUT_DT) = TRUNC(:scheduledDate) ";

    private static final String SET_CHANGE_DATE =
            "UPDATE AMS_NETWORK_CHANGE_REQUESTS "
          + "   SET SCHEDULED_DT = :scheduledDate, NCR_STATUS_CD = 'SCHEDULED',"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE NCR_ID = :ncrId ";

    /** SCHEDULED becomes SUBMITTED; anything else is left alone. */
    private static final String CLEAR_CHANGE_DATE =
            "UPDATE AMS_NETWORK_CHANGE_REQUESTS "
          + "   SET SCHEDULED_DT = NULL,"
          + "       NCR_STATUS_CD = CASE WHEN NCR_STATUS_CD = 'SCHEDULED' THEN 'SUBMITTED'"
          + "                            ELSE NCR_STATUS_CD END,"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE NCR_ID = :ncrId ";

    private static final String RESOLVE_REGION =
            "SELECT R.REGION_CD FROM AMS_ASSETS A "
          + "  JOIN AMS_ADDRESSES D ON D.ADDRESS_ID = A.INSTALL_ADDRESS_ID "
          + "  JOIN AMS_INSTALL_REGIONS R ON R.ZIP_CODE = D.ZIP_CODE "
          + " WHERE A.ASSET_ID = :assetId ";

    private static final String LATEST_CIRCUIT_ID =
            "SELECT * FROM ( SELECT CIRCUIT_ID FROM AMS_ASSET_CONFIGS "
          + "                WHERE ASSET_ID = :assetId ORDER BY REVISION_NUM DESC ) WHERE ROWNUM <= 1 ";

    private static final String LOCK_CIRCUIT_SLOT =
            "SELECT TIMESLOT_ID, CAPACITY, RESERVED_COUNT, AVAILABLE_FL FROM AMS_TIMESLOTS "
          + " WHERE CALL_TYPE_CD = 'CIRCUIT' AND REGION_CD = :region "
          + "   AND TRUNC(START_TM) = TRUNC(:scheduledDate) FOR UPDATE ";

    private static final String EXISTING_CIRCUIT_WINDOW =
            "SELECT CIRCUIT_WINDOW_ID FROM AMS_CIRCUIT_WINDOWS "
          + " WHERE NCR_ID = :ncrId AND TIMESLOT_ID = :timeslotId "
          + "   AND WINDOW_STATUS_CD = 'RESERVED' ";

    private static final String NEXT_CIRCUIT_WINDOW_ID =
            "SELECT " + CommonConstants.SEQ_CIRCUIT_WINDOWS + ".NEXTVAL FROM DUAL ";

    private static final String INSERT_CIRCUIT_WINDOW =
            "INSERT INTO AMS_CIRCUIT_WINDOWS "
          + "       ( CIRCUIT_WINDOW_ID, NCR_ID, ASSET_ID, TIMESLOT_ID, CIRCUIT_ID, SITE_TYPE_CD,"
          + "         WINDOW_DT, WINDOW_STATUS_CD, CREATED_DT, CREATED_BY, MODIFIED_DT, MODIFIED_BY ) "
          + "VALUES ( :windowId, :ncrId, :assetId, :timeslotId, :circuitId, :siteType,"
          + "         :scheduledDate, 'RESERVED', SYSTIMESTAMP, :userId, SYSTIMESTAMP, :userId ) ";

    private static final String INCREMENT_RESERVED =
            "UPDATE AMS_TIMESLOTS SET RESERVED_COUNT = NVL(RESERVED_COUNT, 0) + 1 "
          + " WHERE TIMESLOT_ID = :timeslotId ";

    private static final String DECREMENT_RESERVED =
            "UPDATE AMS_TIMESLOTS "
          + "   SET RESERVED_COUNT = GREATEST(NVL(RESERVED_COUNT, 0) - 1, 0) "
          + " WHERE TIMESLOT_ID = :timeslotId ";

    /** Ascending timeslot id, so concurrent callers touch rows in the same order. */
    private static final String HELD_MOVE_RESERVATIONS =
            "SELECT RESERVATION_ID, TIMESLOT_ID FROM AMS_TIMESLOT_RESERVATIONS "
          + " WHERE ENTITY_TYPE_CD = 'NCR' AND ENTITY_ID = :ncrId "
          + "   AND RESERVATION_STATUS_CD = 'HELD' ORDER BY TIMESLOT_ID ";

    private static final String RELEASE_MOVE_RESERVATION =
            "UPDATE AMS_TIMESLOT_RESERVATIONS "
          + "   SET RESERVATION_STATUS_CD = 'RELEASED', RELEASE_REASON = SUBSTR(:reason, 1, 400),"
          + "       RELEASED_DT = SYSTIMESTAMP, MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE RESERVATION_ID = :reservationId ";

    private static final String RESERVED_CIRCUIT_WINDOWS =
            "SELECT CIRCUIT_WINDOW_ID, TIMESLOT_ID FROM AMS_CIRCUIT_WINDOWS "
          + " WHERE NCR_ID = :ncrId AND WINDOW_STATUS_CD = 'RESERVED' ORDER BY TIMESLOT_ID ";

    private static final String RELEASE_CIRCUIT_WINDOW =
            "UPDATE AMS_CIRCUIT_WINDOWS "
          + "   SET WINDOW_STATUS_CD = 'RELEASED', RELEASE_REASON = SUBSTR(:reason, 1, 400),"
          + "       RELEASED_DT = SYSTIMESTAMP, MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE CIRCUIT_WINDOW_ID = :windowId ";

    /** Books the change date for a device-only change. */
    public SchedulingResult scheduleChangeDate(final long ncrId, final Date scheduledDate,
            final String userId) {
        if (ncrId == 0L || scheduledDate == null) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> bookChangeDate(ncrId, scheduledDate, userId).result);
    }

    /**
     * Books the change date <em>and</em> a circuit window.
     *
     * <p>The two are one unit, which is the whole reason this is a separate operation: if circuit
     * capacity is gone, the change date written moments earlier is undone too, so the request is
     * left exactly as it was rather than half scheduled.</p>
     */
    public SchedulingResult scheduleSiteTypeChange(final long ncrId, final long assetId,
            final String siteTypeCode, final Date scheduledDate, final String userId) {
        if (ncrId == 0L || scheduledDate == null) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> doScheduleSiteType(ncrId, assetId, siteTypeCode, scheduledDate,
                userId));
    }

    private SchedulingResult doScheduleSiteType(final long ncrId, final long assetId,
            final String siteTypeCode, final Date scheduledDate, final String userId) {
        final BookingOutcome booked = bookChangeDate(ncrId, scheduledDate, userId);
        if (!booked.result.isOk()) {
            return booked.result;
        }

        final Long effectiveAsset = assetId != 0L ? Long.valueOf(assetId) : booked.assetId;
        if (effectiveAsset == null) {
            return new SchedulingResult(SchedulingStatus.NO_ASSET);
        }

        final String siteType = siteTypeCode == null || siteTypeCode.trim().isEmpty()
                ? null : siteTypeCode.trim().toUpperCase(Locale.ENGLISH);
        // A null site type is a valid booking; a non-null one has to be a type we recognise.
        if (siteType != null && !SITE_TYPES.contains(siteType)) {
            return new SchedulingResult(SchedulingStatus.INVALID_SITE_TYPE);
        }

        // Not fatal when absent: the window is still bookable without a circuit reference.
        final List<String> circuits = getNamedParameterJdbcTemplate().queryForList(LATEST_CIRCUIT_ID,
                ParameterRepository.of(CommonConstants.PARAM_ASSET_ID, effectiveAsset).build(),
                String.class);
        final String circuitId = circuits.isEmpty() ? null : circuits.get(0);

        final List<Map<String, Object>> slots = getNamedParameterJdbcTemplate().queryForList(
                LOCK_CIRCUIT_SLOT, ParameterRepository.create()
                        .with("region", resolveRegion(effectiveAsset))
                        .with("scheduledDate", scheduledDate)
                        .build());
        if (slots.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.NO_CIRCUIT_WINDOW);
        }
        if (slots.size() > 1) {
            // Two circuit slots for one region and day is a seeding fault, not a business outcome.
            return new SchedulingResult(SchedulingStatus.ERROR);
        }
        final Map<String, Object> slot = slots.get(0);
        if (!"Y".equals(slot.get("AVAILABLE_FL"))) {
            return new SchedulingResult(SchedulingStatus.CIRCUIT_WINDOW_CLOSED);
        }
        final Long timeslotId = Long.valueOf(((Number) slot.get("TIMESLOT_ID")).longValue());

        // Already holding a window on this slot? Reuse it rather than taking a second place.
        final List<Long> existing = getNamedParameterJdbcTemplate().queryForList(
                EXISTING_CIRCUIT_WINDOW, ParameterRepository.create()
                        .with("ncrId", Long.valueOf(ncrId))
                        .with("timeslotId", timeslotId)
                        .build(), Long.class);
        if (!existing.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.OK).withCircuitWindowId(existing.get(0));
        }

        final int capacity = toInt(slot.get("CAPACITY"));
        final int reserved = toInt(slot.get("RESERVED_COUNT"));
        if (reserved >= capacity) {
            // The savepoint undoes the change date written by bookChangeDate above.
            return new SchedulingResult(SchedulingStatus.NO_CAPACITY);
        }

        getNamedParameterJdbcTemplate().update(INCREMENT_RESERVED,
                ParameterRepository.of("timeslotId", timeslotId).build());

        final Long windowId = getNamedParameterJdbcTemplate().queryForObject(
                NEXT_CIRCUIT_WINDOW_ID, ParameterRepository.create().build(), Long.class);
        getNamedParameterJdbcTemplate().update(INSERT_CIRCUIT_WINDOW, ParameterRepository.create()
                .with("windowId", windowId)
                .with("ncrId", Long.valueOf(ncrId))
                .with(CommonConstants.PARAM_ASSET_ID, effectiveAsset)
                .with("timeslotId", timeslotId)
                .with("circuitId", circuitId)
                .with("siteType", siteType)
                .with("scheduledDate", scheduledDate)
                .with(CommonConstants.PARAM_USER_ID, userId)
                .build());
        return new SchedulingResult(SchedulingStatus.OK).withCircuitWindowId(windowId);
    }

    /** Releases the change date and any move timeslots, leaving the request open. */
    public SchedulingResult cancelChangeDate(final long ncrId, final String reason,
            final String userId) {
        if (ncrId == 0L) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> doCancel(ncrId, reason, userId, false));
    }

    /** As above, and gives back every circuit window the request holds. */
    public SchedulingResult cancelSiteTypeChange(final long ncrId, final String reason,
            final String userId) {
        if (ncrId == 0L) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> doCancel(ncrId, reason, userId, true));
    }

    private SchedulingResult doCancel(final long ncrId, final String reason, final String userId,
            final boolean releaseCircuitWindows) {
        final List<Map<String, Object>> locked = getNamedParameterJdbcTemplate().queryForList(
                LOCK_REQUEST, ParameterRepository.of("ncrId", Long.valueOf(ncrId)).build());
        if (locked.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.NOT_FOUND);
        }
        final String status = (String) locked.get(0).get("NCR_STATUS_CD");
        final Object scheduled = locked.get(0).get("SCHEDULED_DT");

        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            return new SchedulingResult(SchedulingStatus.NOT_OPEN);
        }
        if ("INPROG".equals(status)) {
            return new SchedulingResult(SchedulingStatus.IN_PROGRESS);
        }

        final int movesReleased = releaseMoveTimeslots(ncrId, reason, userId);
        int windowsReleased = 0;
        if (releaseCircuitWindows) {
            windowsReleased = releaseCircuitWindows(ncrId, reason, userId);
        }

        if (scheduled == null && movesReleased == 0 && windowsReleased == 0) {
            return new SchedulingResult(SchedulingStatus.NOT_SCHEDULED);
        }

        getNamedParameterJdbcTemplate().update(CLEAR_CHANGE_DATE, ParameterRepository.create()
                .with("ncrId", Long.valueOf(ncrId))
                .with(CommonConstants.PARAM_USER_ID, userId)
                .build());
        return new SchedulingResult(SchedulingStatus.OK).withReleasedCount(windowsReleased);
    }

    private int releaseMoveTimeslots(final long ncrId, final String reason, final String userId) {
        final List<Map<String, Object>> held = getNamedParameterJdbcTemplate().queryForList(
                HELD_MOVE_RESERVATIONS, ParameterRepository.of("ncrId", Long.valueOf(ncrId)).build());
        for (final Map<String, Object> row : held) {
            getNamedParameterJdbcTemplate().update(DECREMENT_RESERVED,
                    ParameterRepository.of("timeslotId", row.get("TIMESLOT_ID")).build());
            getNamedParameterJdbcTemplate().update(RELEASE_MOVE_RESERVATION,
                    ParameterRepository.create()
                            .with("reason", reason)
                            .with("reservationId", row.get("RESERVATION_ID"))
                            .with(CommonConstants.PARAM_USER_ID, userId)
                            .build());
        }
        return held.size();
    }

    private int releaseCircuitWindows(final long ncrId, final String reason, final String userId) {
        final List<Map<String, Object>> windows = getNamedParameterJdbcTemplate().queryForList(
                RESERVED_CIRCUIT_WINDOWS,
                ParameterRepository.of("ncrId", Long.valueOf(ncrId)).build());
        for (final Map<String, Object> row : windows) {
            getNamedParameterJdbcTemplate().update(DECREMENT_RESERVED,
                    ParameterRepository.of("timeslotId", row.get("TIMESLOT_ID")).build());
            getNamedParameterJdbcTemplate().update(RELEASE_CIRCUIT_WINDOW,
                    ParameterRepository.create()
                            .with("reason", reason)
                            .with("windowId", row.get("CIRCUIT_WINDOW_ID"))
                            .with(CommonConstants.PARAM_USER_ID, userId)
                            .build());
        }
        return windows.size();
    }

    /**
     * The shared first half of both scheduling operations: lock the request, check it is open and
     * the date is acceptable, then write the date.
     */
    private BookingOutcome bookChangeDate(final long ncrId, final Date scheduledDate,
            final String userId) {
        final List<Map<String, Object>> locked = getNamedParameterJdbcTemplate().queryForList(
                LOCK_REQUEST, ParameterRepository.of("ncrId", Long.valueOf(ncrId)).build());
        if (locked.isEmpty()) {
            return new BookingOutcome(new SchedulingResult(SchedulingStatus.NOT_FOUND), null, null);
        }
        final Map<String, Object> request = locked.get(0);
        final String status = (String) request.get("NCR_STATUS_CD");
        final Number customerId = (Number) request.get("CUSTOMER_ID");
        final Number assetId = (Number) request.get("ASSET_ID");

        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            return new BookingOutcome(new SchedulingResult(SchedulingStatus.NOT_OPEN), null, null);
        }
        if ("INPROG".equals(status)) {
            return new BookingOutcome(new SchedulingResult(SchedulingStatus.IN_PROGRESS), null, null);
        }
        if (isBeforeToday(scheduledDate)) {
            return new BookingOutcome(new SchedulingResult(SchedulingStatus.DATE_IN_PAST), null, null);
        }

        final Integer blackouts = getNamedParameterJdbcTemplate().queryForObject(COUNT_BLACKOUTS,
                ParameterRepository.create()
                        .with(CommonConstants.PARAM_CUSTOMER_ID, customerId)
                        .with("scheduledDate", scheduledDate)
                        .build(), Integer.class);
        if (blackouts != null && blackouts.intValue() > 0) {
            return new BookingOutcome(new SchedulingResult(SchedulingStatus.CUSTOMER_BLACKOUT),
                    null, null);
        }

        // Weekends and holidays are deliberately not rejected: carrier work routinely runs
        // overnight and at weekends.
        getNamedParameterJdbcTemplate().update(SET_CHANGE_DATE, ParameterRepository.create()
                .with("scheduledDate", scheduledDate)
                .with("ncrId", Long.valueOf(ncrId))
                .with(CommonConstants.PARAM_USER_ID, userId)
                .build());

        return new BookingOutcome(new SchedulingResult(SchedulingStatus.OK),
                customerId == null ? null : Long.valueOf(customerId.longValue()),
                assetId == null ? null : Long.valueOf(assetId.longValue()));
    }

    private String resolveRegion(final Long assetId) {
        final List<String> regions = getNamedParameterJdbcTemplate().queryForList(RESOLVE_REGION,
                ParameterRepository.of(CommonConstants.PARAM_ASSET_ID, assetId).build(),
                String.class);
        // No mapping, or an ambiguous one, falls back rather than failing the booking.
        return regions.size() == 1 ? regions.get(0) : FALLBACK_REGION;
    }

    private static boolean isBeforeToday(final Date date) {
        final java.util.Calendar today = java.util.Calendar.getInstance();
        final java.util.Calendar wanted = java.util.Calendar.getInstance();
        wanted.setTime(date);
        for (final java.util.Calendar calendar : new java.util.Calendar[] {today, wanted}) {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
            calendar.set(java.util.Calendar.MINUTE, 0);
            calendar.set(java.util.Calendar.SECOND, 0);
            calendar.set(java.util.Calendar.MILLISECOND, 0);
        }
        return wanted.before(today);
    }

    private SchedulingResult inSavepoint(final java.util.function.Supplier<SchedulingResult> op) {
        return SavepointScope.run(dataSource(), op);
    }

    private DataSource dataSource() {
        return ((JdbcTemplate) getNamedParameterJdbcTemplate().getJdbcOperations()).getDataSource();
    }

    private static int toInt(final Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** The change-date booking plus the two ids its caller needs afterwards. */
    private static final class BookingOutcome {
        private final SchedulingResult result;
        private final Long customerId;
        private final Long assetId;

        BookingOutcome(final SchedulingResult result, final Long customerId, final Long assetId) {
            this.result = result;
            this.customerId = customerId;
            this.assetId = assetId;
        }
    }
}
