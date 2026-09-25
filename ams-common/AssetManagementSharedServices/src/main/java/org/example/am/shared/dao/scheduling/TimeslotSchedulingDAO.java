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
 * connection, and a commit would destroy its ability to roll back - placing an install order
 * writes the order, its installation and the reservation as one abandonable unit. Each
 * operation takes a savepoint instead and rolls back to it on any outcome other than {@code OK},
 * so a status other than {@code OK} reliably means nothing was changed.</p>
 */
@Repository("timeslotSchedulingDAO")
public class TimeslotSchedulingDAO extends BaseDAO {

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
