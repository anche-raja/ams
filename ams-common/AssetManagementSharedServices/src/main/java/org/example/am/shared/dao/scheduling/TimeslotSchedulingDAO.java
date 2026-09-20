package org.example.am.shared.dao.scheduling;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.stereotype.Repository;

/**
 * Takes and gives back places on the calendar.
 *
 * <p>Ported from {@code AMS_SCHEDULING_PG.reserve_timeslot} and {@code cancel_timeslot}. The
 * package body is kept under {@code db/oracle/06_packages} as the original specification; read it
 * alongside this class if you change either.</p>
 *
 * <p>Three properties carry over and must not be lost:</p>
 *
 * <ul>
 *   <li><strong>The ledger is the source of truth, not the counter.</strong>
 *       {@code AMS_TIMESLOT_RESERVATIONS} records who holds each place. Reserving a slot this
 *       entity already holds returns {@code OK} without taking a second place, and cancelling one
 *       it does not hold returns {@code NOT_RESERVED} rather than failing. Without the ledger a
 *       double cancel would decrement blind and eventually hand the same place out twice.</li>
 *   <li><strong>The capacity test and the increment happen under a row lock</strong>, so two
 *       operators booking the last place cannot both succeed - the loser gets {@code NO_CAPACITY}
 *       rather than an exception, and the {@code TIMESLOTS_RESERVED_CK} constraint is the backstop
 *       if this ever goes wrong.</li>
 *   <li><strong>{@code AVAILABLE_FL} is never written.</strong> It means "ops opened this slot",
 *       not "this slot has room". Fullness is {@code RESERVED_COUNT >= CAPACITY}. Flipping the flag
 *       on filling would let a later cancel re-open a slot ops had closed by hand.</li>
 * </ul>
 *
 * <p>Nothing here commits. The caller is inside a Spring {@code @Transactional} sharing this
 * connection, and a commit would destroy its ability to roll back -
 * {@code RescheduleNcrAction} depends on cancel-then-reserve being one abandonable unit. Each
 * operation takes a savepoint instead and rolls back to it on any outcome other than {@code OK},
 * so a status other than {@code OK} reliably means nothing was changed.</p>
 */
@Repository("timeslotSchedulingDAO")
public class TimeslotSchedulingDAO extends BaseDAO {

    /** Matches the PL/SQL default when MAXDECOMDAYS is absent. */
    private static final int DEFAULT_MAX_DECOMMISSION_DAYS = 42;

    @org.springframework.beans.factory.annotation.Autowired
    private org.example.am.shared.service.ConfigService configService;

    /** The only entity types that may hold a place. */
    private static final Set<String> ENTITY_TYPES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("INSTALL", "TECHLINE", "NCR", "SHIP")));

    private static final String LOCK_TIMESLOT =
            "SELECT CAPACITY, RESERVED_COUNT, AVAILABLE_FL, START_TM "
          + "  FROM AMS_TIMESLOTS WHERE TIMESLOT_ID = :timeslotId FOR UPDATE ";

    private static final String COUNT_HELD =
            "SELECT COUNT(*) FROM AMS_TIMESLOT_RESERVATIONS "
          + " WHERE TIMESLOT_ID = :timeslotId AND ENTITY_TYPE_CD = :entityType "
          + "   AND ENTITY_ID = :entityId AND RESERVATION_STATUS_CD = 'HELD' ";

    private static final String INCREMENT_RESERVED =
            "UPDATE AMS_TIMESLOTS SET RESERVED_COUNT = NVL(RESERVED_COUNT, 0) + 1 "
          + " WHERE TIMESLOT_ID = :timeslotId ";

    private static final String NEXT_RESERVATION_ID =
            "SELECT " + CommonConstants.SEQ_TIMESLOT_RESERVATIONS + ".NEXTVAL FROM DUAL ";

    private static final String INSERT_RESERVATION =
            "INSERT INTO AMS_TIMESLOT_RESERVATIONS "
          + "       ( RESERVATION_ID, TIMESLOT_ID, ENTITY_TYPE_CD, ENTITY_ID, SCHEDULED_DT,"
          + "         RESERVATION_STATUS_CD, CREATED_DT, CREATED_BY, MODIFIED_DT, MODIFIED_BY ) "
          + "VALUES ( :reservationId, :timeslotId, :entityType, :entityId, :scheduledDate,"
          + "         'HELD', SYSTIMESTAMP, :userId, SYSTIMESTAMP, :userId ) ";

    /**
     * Re-booking an already scheduled visit is a reschedule, which the status has to show - the
     * calendar and the customer notification both read it.
     */
    private static final String SCHEDULE_INSTALLATION =
            "UPDATE AMS_INSTALLATIONS "
          + "   SET TIMESLOT_ID = :timeslotId,"
          + "       SCHEDULED_DT = :scheduledDate,"
          + "       INSTALL_STATUS_CD = CASE WHEN INSTALL_STATUS_CD IN ('SCHEDULED', 'RESCHED')"
          + "                                THEN 'RESCHED' ELSE 'SCHEDULED' END,"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE ORDER_ID = :entityId ";

    private static final String SCHEDULE_ORDER =
            "UPDATE AMS_ORDERS SET ORDER_STATUS_CD = 'SCHEDULED',"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE ORDER_ID = :entityId AND ORDER_STATUS_CD NOT IN ('CANCELLED', 'COMPLETED') ";

    private static final String LOCK_TIMESLOT_ONLY =
            "SELECT TIMESLOT_ID FROM AMS_TIMESLOTS WHERE TIMESLOT_ID = :timeslotId FOR UPDATE ";

    private static final String LOCK_HELD_RESERVATION =
            "SELECT RESERVATION_ID FROM AMS_TIMESLOT_RESERVATIONS "
          + " WHERE TIMESLOT_ID = :timeslotId AND ENTITY_TYPE_CD = :entityType "
          + "   AND ENTITY_ID = :entityId AND RESERVATION_STATUS_CD = 'HELD' FOR UPDATE ";

    private static final String RELEASE_RESERVATION =
            "UPDATE AMS_TIMESLOT_RESERVATIONS "
          + "   SET RESERVATION_STATUS_CD = 'RELEASED', RELEASED_DT = SYSTIMESTAMP,"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE RESERVATION_ID = :reservationId ";

    /**
     * GREATEST floors the count at zero. The check constraint would catch a negative anyway, but a
     * constraint violation in front of a user is a worse outcome than a clamped count.
     */
    private static final String DECREMENT_RESERVED =
            "UPDATE AMS_TIMESLOTS "
          + "   SET RESERVED_COUNT = GREATEST(NVL(RESERVED_COUNT, 0) - 1, 0) "
          + " WHERE TIMESLOT_ID = :timeslotId ";

    private static final String UNSCHEDULE_INSTALLATION =
            "UPDATE AMS_INSTALLATIONS "
          + "   SET TIMESLOT_ID = NULL, SCHEDULED_DT = NULL, INSTALL_STATUS_CD = 'NOTSCHED',"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE ORDER_ID = :entityId AND TIMESLOT_ID = :timeslotId ";

    /**
     * Takes one place on a calendar slot.
     *
     * <p>For {@code INSTALL} the entity id is the <em>order</em> id, not the installation id.</p>
     */
    public SchedulingResult reserve(final long timeslotId, final long entityId,
            final String entityTypeCode, final Date scheduledDate, final String userId) {
        final String entityType = normalise(entityTypeCode);
        if (timeslotId == 0L || entityId == 0L || !ENTITY_TYPES.contains(entityType)) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> doReserve(timeslotId, entityId, entityType, scheduledDate, userId));
    }

    private SchedulingResult doReserve(final long timeslotId, final long entityId,
            final String entityType, final Date scheduledDate, final String userId) {
        final List<java.util.Map<String, Object>> locked = getNamedParameterJdbcTemplate()
                .queryForList(LOCK_TIMESLOT,
                        ParameterRepository.of("timeslotId", Long.valueOf(timeslotId)).build());
        if (locked.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.NOT_FOUND);
        }
        final java.util.Map<String, Object> slot = locked.get(0);

        if (!"Y".equals(slot.get("AVAILABLE_FL"))) {
            return new SchedulingResult(SchedulingStatus.SLOT_CLOSED);
        }

        // Already held by this entity? Success, without taking a second place. A resubmitted form
        // must not error and must not consume capacity twice.
        if (countHeld(timeslotId, entityType, entityId) > 0) {
            return new SchedulingResult(SchedulingStatus.OK);
        }

        final int capacity = toInt(slot.get("CAPACITY"));
        final int reserved = toInt(slot.get("RESERVED_COUNT"));
        if (reserved >= capacity) {
            return new SchedulingResult(SchedulingStatus.NO_CAPACITY);
        }

        final Date effectiveDate = scheduledDate != null
                ? scheduledDate : (Date) slot.get("START_TM");

        getNamedParameterJdbcTemplate().update(INCREMENT_RESERVED,
                ParameterRepository.of("timeslotId", Long.valueOf(timeslotId)).build());

        final Long reservationId = getNamedParameterJdbcTemplate().queryForObject(
                NEXT_RESERVATION_ID, ParameterRepository.create().build(), Long.class);
        getNamedParameterJdbcTemplate().update(INSERT_RESERVATION, ParameterRepository.create()
                .with("reservationId", reservationId)
                .with("timeslotId", Long.valueOf(timeslotId))
                .with("entityType", entityType)
                .with("entityId", Long.valueOf(entityId))
                .with("scheduledDate", effectiveDate)
                .with(CommonConstants.PARAM_USER_ID, userId)
                .build());

        if ("INSTALL".equals(entityType)) {
            final int updated = getNamedParameterJdbcTemplate().update(SCHEDULE_INSTALLATION,
                    ParameterRepository.create()
                            .with("timeslotId", Long.valueOf(timeslotId))
                            .with("scheduledDate", effectiveDate)
                            .with("entityId", Long.valueOf(entityId))
                            .with(CommonConstants.PARAM_USER_ID, userId)
                            .build());
            if (updated == 0) {
                // Booking an engineer for an order with no installation record would strand the
                // capacity. The savepoint gives the place back.
                return new SchedulingResult(SchedulingStatus.NO_INSTALLATION);
            }
            getNamedParameterJdbcTemplate().update(SCHEDULE_ORDER, ParameterRepository.create()
                    .with("entityId", Long.valueOf(entityId))
                    .with(CommonConstants.PARAM_USER_ID, userId)
                    .build());
        }

        // TECHLINE has no entity table of its own, and for NCR this is the move timeslot rather
        // than the change date, so neither needs a further update - the ledger row is the record.
        //
        // SHIP is deliberately in the same position. A despatch window means the warehouse will
        // pick the order, not that an engineer has been booked, so unlike INSTALL it must not move
        // the order to SCHEDULED.
        return new SchedulingResult(SchedulingStatus.OK);
    }

    /** Gives a place back. Cancelling one that is not held changes nothing. */
    public SchedulingResult cancel(final long timeslotId, final long entityId,
            final String entityTypeCode, final String userId) {
        final String entityType = normalise(entityTypeCode);
        if (timeslotId == 0L || entityId == 0L || entityType.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> doCancel(timeslotId, entityId, entityType, userId));
    }

    private SchedulingResult doCancel(final long timeslotId, final long entityId,
            final String entityType, final String userId) {
        final List<Long> slot = getNamedParameterJdbcTemplate().queryForList(LOCK_TIMESLOT_ONLY,
                ParameterRepository.of("timeslotId", Long.valueOf(timeslotId)).build(), Long.class);
        if (slot.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.NOT_FOUND);
        }

        final List<Long> held = getNamedParameterJdbcTemplate().queryForList(LOCK_HELD_RESERVATION,
                ParameterRepository.create()
                        .with("timeslotId", Long.valueOf(timeslotId))
                        .with("entityType", entityType)
                        .with("entityId", Long.valueOf(entityId))
                        .build(), Long.class);
        if (held.isEmpty()) {
            // Nothing held, so nothing to give back. This is what makes a double cancel harmless.
            return new SchedulingResult(SchedulingStatus.NOT_RESERVED);
        }

        getNamedParameterJdbcTemplate().update(RELEASE_RESERVATION, ParameterRepository.create()
                .with("reservationId", held.get(0))
                .with(CommonConstants.PARAM_USER_ID, userId)
                .build());
        getNamedParameterJdbcTemplate().update(DECREMENT_RESERVED,
                ParameterRepository.of("timeslotId", Long.valueOf(timeslotId)).build());

        if ("INSTALL".equals(entityType)) {
            getNamedParameterJdbcTemplate().update(UNSCHEDULE_INSTALLATION,
                    ParameterRepository.create()
                            .with("entityId", Long.valueOf(entityId))
                            .with("timeslotId", Long.valueOf(timeslotId))
                            .with(CommonConstants.PARAM_USER_ID, userId)
                            .build());
        }
        return new SchedulingResult(SchedulingStatus.OK);
    }

    private int countHeld(final long timeslotId, final String entityType, final long entityId) {
        final Integer count = getNamedParameterJdbcTemplate().queryForObject(COUNT_HELD,
                ParameterRepository.create()
                        .with("timeslotId", Long.valueOf(timeslotId))
                        .with("entityType", entityType)
                        .with("entityId", Long.valueOf(entityId))
                        .build(), Integer.class);
        return count == null ? 0 : count.intValue();
    }


    // ------------------------------------------------------------------
    // Decommission dates
    // ------------------------------------------------------------------

    private static final String SELECT_ASSET_STATUS =
            "SELECT ASSET_STATUS_CD FROM AMS_ASSETS WHERE ASSET_ID = :assetId ";

    /**
     * Two statements rather than one, and the reason survives the port even though its cause does
     * not: Oracle rejects FOR UPDATE against an inline view carrying an ORDER BY (ORA-02014), so
     * the newest open decommission is identified first and locked by id second. Status and asset
     * are then re-read under the lock, so nothing is trusted from the unlocked read.
     */
    private static final String SELECT_NEWEST_OPEN_DECOMMISSION =
            "SELECT * FROM ( SELECT DECOMMISSION_ID FROM AMS_DECOMMISSIONS "
          + "                WHERE ASSET_ID = :assetId "
          + "                  AND DECOM_STATUS_CD IN ('REQUESTED', 'SCHEDULED') "
          + "                ORDER BY REQUESTED_DT DESC, DECOMMISSION_ID DESC ) WHERE ROWNUM <= 1 ";

    private static final String NEXT_DECOMMISSION_ID =
            "SELECT " + CommonConstants.SEQ_DECOMMISSIONS + ".NEXTVAL FROM DUAL ";

    private static final String INSERT_DECOMMISSION =
            "INSERT INTO AMS_DECOMMISSIONS "
          + "       ( DECOMMISSION_ID, ASSET_ID, DECOM_STATUS_CD, REQUESTED_DT,"
          + "         HARDWARE_RETURN_FL, CREATED_DT, CREATED_BY, MODIFIED_DT, MODIFIED_BY ) "
          + "VALUES ( :decommissionId, :assetId, 'REQUESTED', SYSTIMESTAMP,"
          + "         :hardwareReturnFlag, SYSTIMESTAMP, :userId, SYSTIMESTAMP, :userId ) ";

    private static final String LOCK_DECOMMISSION =
            "SELECT DECOMMISSION_ID, DECOM_STATUS_CD, ASSET_ID, SCHEDULED_DT "
          + "  FROM AMS_DECOMMISSIONS WHERE DECOMMISSION_ID = :decommissionId FOR UPDATE ";

    private static final String SCHEDULE_DECOMMISSION =
            "UPDATE AMS_DECOMMISSIONS "
          + "   SET SCHEDULED_DT = :scheduledDate, DECOM_STATUS_CD = 'SCHEDULED',"
          + "       HARDWARE_RETURN_FL = :hardwareReturnFlag,"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE DECOMMISSION_ID = :decommissionId ";

    private static final String MARK_ASSET_PENDING_DECOMMISSION =
            "UPDATE AMS_ASSETS SET ASSET_STATUS_CD = 'PENDDECOM',"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE ASSET_ID = :assetId AND ASSET_STATUS_CD IN ('ACTIVE', 'INSTALLED') ";

    /** Back to REQUESTED, never to CANCELLED: this releases the date, not the request. */
    private static final String RELEASE_DECOMMISSION_DATE =
            "UPDATE AMS_DECOMMISSIONS "
          + "   SET SCHEDULED_DT = NULL, DECOM_STATUS_CD = 'REQUESTED',"
          + "       REASON = SUBSTR(:reason, 1, 400),"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE DECOMMISSION_ID = :decommissionId ";

    private static final String COUNT_OTHER_SCHEDULED =
            "SELECT COUNT(*) FROM AMS_DECOMMISSIONS "
          + " WHERE ASSET_ID = :assetId AND DECOMMISSION_ID <> :decommissionId "
          + "   AND DECOM_STATUS_CD = 'SCHEDULED' ";

    private static final String REACTIVATE_ASSET =
            "UPDATE AMS_ASSETS SET ASSET_STATUS_CD = 'ACTIVE',"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE ASSET_ID = :assetId AND ASSET_STATUS_CD = 'PENDDECOM' ";

    /**
     * Books the date an asset is taken out of service.
     *
     * <p>A decommission id of zero means "whichever one is open, or raise a new one" - which is
     * what {@code CalendarServiceImpl} passes where the Java value was null.</p>
     *
     * <p>The date gates are re-checked here even though the service already refuses them, so a
     * caller that bypassed the service gets a status rather than a bad booking.</p>
     */
    public SchedulingResult scheduleDecommission(final long decommissionId, final long assetId,
            final Date scheduledDate, final boolean hardwareReturnRequired, final String userId) {
        if (assetId == 0L || scheduledDate == null) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> doScheduleDecommission(decommissionId, assetId, scheduledDate,
                hardwareReturnRequired ? "Y" : "N", userId));
    }

    private SchedulingResult doScheduleDecommission(final long decommissionId, final long assetId,
            final Date scheduledDate, final String flag, final String userId) {
        final List<String> assetStatus = getNamedParameterJdbcTemplate().queryForList(
                SELECT_ASSET_STATUS,
                ParameterRepository.of(CommonConstants.PARAM_ASSET_ID, Long.valueOf(assetId)).build(),
                String.class);
        if (assetStatus.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.NOT_FOUND);
        }
        if ("DECOM".equals(assetStatus.get(0)) || "CANCELLED".equals(assetStatus.get(0))) {
            return new SchedulingResult(SchedulingStatus.NOT_OPEN);
        }

        final SchedulingResult dateProblem = checkDecommissionDate(scheduledDate);
        if (dateProblem != null) {
            return dateProblem;
        }

        Long targetId = decommissionId == 0L ? null : Long.valueOf(decommissionId);
        if (targetId == null) {
            final List<Long> open = getNamedParameterJdbcTemplate().queryForList(
                    SELECT_NEWEST_OPEN_DECOMMISSION,
                    ParameterRepository.of(CommonConstants.PARAM_ASSET_ID, Long.valueOf(assetId))
                            .build(), Long.class);
            if (open.isEmpty()) {
                targetId = getNamedParameterJdbcTemplate().queryForObject(NEXT_DECOMMISSION_ID,
                        ParameterRepository.create().build(), Long.class);
                getNamedParameterJdbcTemplate().update(INSERT_DECOMMISSION,
                        ParameterRepository.create()
                                .with("decommissionId", targetId)
                                .with(CommonConstants.PARAM_ASSET_ID, Long.valueOf(assetId))
                                .with("hardwareReturnFlag", flag)
                                .with(CommonConstants.PARAM_USER_ID, userId)
                                .build());
            } else {
                targetId = open.get(0);
            }
        }

        final List<java.util.Map<String, Object>> locked = getNamedParameterJdbcTemplate()
                .queryForList(LOCK_DECOMMISSION,
                        ParameterRepository.of("decommissionId", targetId).build());
        if (locked.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.NOT_FOUND);
        }
        final String status = (String) locked.get(0).get("DECOM_STATUS_CD");
        final long owningAsset = ((Number) locked.get(0).get("ASSET_ID")).longValue();

        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            return new SchedulingResult(SchedulingStatus.NOT_OPEN);
        }
        if ("INPROG".equals(status)) {
            return new SchedulingResult(SchedulingStatus.IN_PROGRESS);
        }
        if (owningAsset != assetId) {
            return new SchedulingResult(SchedulingStatus.ASSET_MISMATCH);
        }

        getNamedParameterJdbcTemplate().update(SCHEDULE_DECOMMISSION, ParameterRepository.create()
                .with("scheduledDate", scheduledDate)
                .with("hardwareReturnFlag", flag)
                .with("decommissionId", targetId)
                .with(CommonConstants.PARAM_USER_ID, userId)
                .build());
        getNamedParameterJdbcTemplate().update(MARK_ASSET_PENDING_DECOMMISSION,
                ParameterRepository.create()
                        .with(CommonConstants.PARAM_ASSET_ID, Long.valueOf(assetId))
                        .with(CommonConstants.PARAM_USER_ID, userId)
                        .build());
        return new SchedulingResult(SchedulingStatus.OK);
    }

    /** @return a refusal, or {@code null} when the date is acceptable */
    private SchedulingResult checkDecommissionDate(final Date scheduledDate) {
        final java.util.Calendar today = java.util.Calendar.getInstance();
        truncate(today);
        final java.util.Calendar wanted = java.util.Calendar.getInstance();
        wanted.setTime(scheduledDate);
        truncate(wanted);

        if (wanted.before(today)) {
            return new SchedulingResult(SchedulingStatus.DATE_IN_PAST);
        }
        final int maxDays = configService.getInt(
                org.example.am.shared.domain.PropertyType.MAX_DECOMMISSION_SCHEDULING_DAYS,
                DEFAULT_MAX_DECOMMISSION_DAYS);
        final java.util.Calendar limit = java.util.Calendar.getInstance();
        truncate(limit);
        limit.add(java.util.Calendar.DAY_OF_MONTH, maxDays);
        if (wanted.after(limit)) {
            return new SchedulingResult(SchedulingStatus.OUTSIDE_WINDOW);
        }
        return null;
    }

    private static void truncate(final java.util.Calendar calendar) {
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
    }

    /** Releases the date without cancelling the request. */
    public SchedulingResult cancelDecommission(final long decommissionId, final String reason,
            final String userId) {
        if (decommissionId == 0L) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        return inSavepoint(() -> doCancelDecommission(decommissionId, reason, userId));
    }

    private SchedulingResult doCancelDecommission(final long decommissionId, final String reason,
            final String userId) {
        final List<java.util.Map<String, Object>> locked = getNamedParameterJdbcTemplate()
                .queryForList(LOCK_DECOMMISSION,
                        ParameterRepository.of("decommissionId", Long.valueOf(decommissionId)).build());
        if (locked.isEmpty()) {
            return new SchedulingResult(SchedulingStatus.NOT_FOUND);
        }
        final String status = (String) locked.get(0).get("DECOM_STATUS_CD");
        final Object scheduled = locked.get(0).get("SCHEDULED_DT");
        final long assetId = ((Number) locked.get(0).get("ASSET_ID")).longValue();

        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            return new SchedulingResult(SchedulingStatus.NOT_OPEN);
        }
        if ("INPROG".equals(status)) {
            return new SchedulingResult(SchedulingStatus.IN_PROGRESS);
        }
        if (scheduled == null) {
            return new SchedulingResult(SchedulingStatus.NOT_SCHEDULED);
        }

        getNamedParameterJdbcTemplate().update(RELEASE_DECOMMISSION_DATE,
                ParameterRepository.create()
                        .with("reason", reason)
                        .with("decommissionId", Long.valueOf(decommissionId))
                        .with(CommonConstants.PARAM_USER_ID, userId)
                        .build());

        // Only put the asset back if nothing else still has it pending decommission.
        final Integer others = getNamedParameterJdbcTemplate().queryForObject(COUNT_OTHER_SCHEDULED,
                ParameterRepository.create()
                        .with(CommonConstants.PARAM_ASSET_ID, Long.valueOf(assetId))
                        .with("decommissionId", Long.valueOf(decommissionId))
                        .build(), Integer.class);
        if (others == null || others.intValue() == 0) {
            getNamedParameterJdbcTemplate().update(REACTIVATE_ASSET, ParameterRepository.create()
                    .with(CommonConstants.PARAM_ASSET_ID, Long.valueOf(assetId))
                    .with(CommonConstants.PARAM_USER_ID, userId)
                    .build());
        }
        return new SchedulingResult(SchedulingStatus.OK);
    }

    private SchedulingResult inSavepoint(final java.util.function.Supplier<SchedulingResult> op) {
        return SavepointScope.run(dataSource(), op);
    }

    private javax.sql.DataSource dataSource() {
        return ((org.springframework.jdbc.core.JdbcTemplate)
                getNamedParameterJdbcTemplate().getJdbcOperations()).getDataSource();
    }

    private static int toInt(final Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String normalise(final String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ENGLISH);
    }
}
