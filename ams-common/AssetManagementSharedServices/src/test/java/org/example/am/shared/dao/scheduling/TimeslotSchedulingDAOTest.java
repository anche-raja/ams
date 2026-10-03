package org.example.am.shared.dao.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.example.am.shared.helper.AbstractBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The reservation logic, exercised for the first time.
 *
 * <p>While this was PL/SQL nothing in the build ever ran it: the only coverage was
 * {@code CalendarServiceImplTest} and {@code OrderServiceImplTest} mocking the DAO and asserting
 * which procedure would have been called. The only thing that ever executed the real logic was
 * {@code db/oracle/09_validate/validate.sql}, inside a container, outside Maven. These cases are
 * ported from it.</p>
 */
public class TimeslotSchedulingDAOTest extends AbstractBaseTest {

    /** Seeded TECHLINE slot: capacity 4, one place already taken. */
    private static final long SLOT_WITH_ROOM = 9701L;

    /** Seeded TECHLINE slot: capacity 2, both places taken. */
    private static final long SLOT_FULL = 9702L;

    /** Seeded INSTALL slot that ops has closed. */
    private static final long SLOT_CLOSED = 9704L;

    private static final long ENTITY_ID = 777001L;

    @Autowired
    private TimeslotSchedulingDAO dao;

    /** The context defines a DataSource, not a template; this wraps it for the assertions. */
    private JdbcTemplate jdbcTemplate;

    @Autowired
    public void setDataSource(final javax.sql.DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    public void aPlaceIsTakenAndTheLedgerRecordsWhoHoldsIt() {
        final int before = reservedCount(SLOT_WITH_ROOM);

        final SchedulingResult result = dao.reserve(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", null, TEST_USER);

        assertEquals(SchedulingStatus.OK, result.getStatus());
        assertEquals(before + 1, reservedCount(SLOT_WITH_ROOM));
        assertEquals(1, heldRows(SLOT_WITH_ROOM, "TECHLINE", ENTITY_ID));
    }

    @Test
    public void afullSlotIsRefusedWithoutAnException() {
        final int before = reservedCount(SLOT_FULL);

        final SchedulingResult result = dao.reserve(SLOT_FULL, ENTITY_ID, "TECHLINE", null, TEST_USER);

        assertEquals(SchedulingStatus.NO_CAPACITY, result.getStatus());
        assertEquals("a refused booking must change nothing", before, reservedCount(SLOT_FULL));
        assertEquals(0, heldRows(SLOT_FULL, "TECHLINE", ENTITY_ID));
    }

    /** A resubmitted form must neither error nor consume a second place. */
    @Test
    public void reservingTwiceDoesNotTakeASecondPlace() {
        dao.reserve(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", null, TEST_USER);
        final int afterFirst = reservedCount(SLOT_WITH_ROOM);

        final SchedulingResult second = dao.reserve(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", null, TEST_USER);

        assertEquals(SchedulingStatus.OK, second.getStatus());
        assertEquals(afterFirst, reservedCount(SLOT_WITH_ROOM));
        assertEquals(1, heldRows(SLOT_WITH_ROOM, "TECHLINE", ENTITY_ID));
    }

    @Test
    public void aSlotOpsHasClosedIsRefused() {
        final SchedulingResult result = dao.reserve(SLOT_CLOSED, ENTITY_ID, "INSTALL", null, TEST_USER);

        assertEquals(SchedulingStatus.SLOT_CLOSED, result.getStatus());
    }

    @Test
    public void anUnknownSlotIsNotFound() {
        assertEquals(SchedulingStatus.NOT_FOUND,
                dao.reserve(987654L, ENTITY_ID, "TECHLINE", null, TEST_USER).getStatus());
    }

    @Test
    public void anUnrecognisedEntityTypeIsRefusedBeforeAnythingIsLocked() {
        assertEquals(SchedulingStatus.INVALID_INPUT,
                dao.reserve(SLOT_WITH_ROOM, ENTITY_ID, "BOGUS", null, TEST_USER).getStatus());
    }

    @Test
    public void cancellingGivesThePlaceBack() {
        dao.reserve(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", null, TEST_USER);
        final int afterReserve = reservedCount(SLOT_WITH_ROOM);

        final SchedulingResult result = dao.cancel(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", TEST_USER);

        assertEquals(SchedulingStatus.OK, result.getStatus());
        assertEquals(afterReserve - 1, reservedCount(SLOT_WITH_ROOM));
        assertEquals("the ledger row must be released, not deleted",
                0, heldRows(SLOT_WITH_ROOM, "TECHLINE", ENTITY_ID));
    }

    /**
     * The reason the ledger exists. Without it a second cancel would decrement blind, and the slot
     * would eventually hand the same place out twice.
     */
    @Test
    public void cancellingTwiceIsHarmlessAndTheCountDoesNotDrift() {
        dao.reserve(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", null, TEST_USER);
        dao.cancel(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", TEST_USER);
        final int afterFirstCancel = reservedCount(SLOT_WITH_ROOM);

        final SchedulingResult second = dao.cancel(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", TEST_USER);

        assertEquals(SchedulingStatus.NOT_RESERVED, second.getStatus());
        assertEquals(afterFirstCancel, reservedCount(SLOT_WITH_ROOM));
    }

    @Test
    public void cancellingSomethingNeverHeldReportsItRatherThanFailing() {
        assertEquals(SchedulingStatus.NOT_RESERVED,
                dao.cancel(SLOT_WITH_ROOM, 424242L, "TECHLINE", TEST_USER).getStatus());
    }

    /**
     * Booking an engineer for an order with no installation record would strand the capacity, so
     * the whole reservation is rolled back - not just abandoned.
     */
    @Test
    public void anInstallWithNoInstallationRecordGivesTheCapacityBack() {
        final int before = reservedCount(9703L);

        final SchedulingResult result = dao.reserve(9703L, 999999L, "INSTALL", null, TEST_USER);

        assertEquals(SchedulingStatus.NO_INSTALLATION, result.getStatus());
        assertEquals("the place must not be stranded", before, reservedCount(9703L));
        assertEquals(0, heldRows(9703L, "INSTALL", 999999L));
    }

    /**
     * AVAILABLE_FL means "ops opened this slot", not "this slot has room". Writing it on filling
     * would let a later cancel re-open a slot ops had closed by hand.
     */
    @Test
    public void fillingASlotNeverWritesTheAvailableFlag() {
        final String before = availableFlag(SLOT_WITH_ROOM);

        dao.reserve(SLOT_WITH_ROOM, ENTITY_ID, "TECHLINE", null, TEST_USER);

        assertEquals(before, availableFlag(SLOT_WITH_ROOM));
    }

    /** A despatch window means the warehouse will pick the order, not that an engineer is booked. */
    @Test
    public void aDespatchWindowDoesNotMoveTheOrderToScheduled() {
        final String before = orderStatus(6002L);

        final SchedulingResult result = dao.reserve(9705L, 6002L, "SHIP", null, TEST_USER);

        assertEquals(SchedulingStatus.OK, result.getStatus());
        assertEquals("SHIP must not schedule the order", before, orderStatus(6002L));
    }

    @Test
    public void theCapacityConstraintBacksTheCounterUp() {
        // Proves the backstop is live: if the ported arithmetic ever lets the count past capacity,
        // the database refuses rather than double-booking an engineer.
        try {
            jdbcTemplate.update("UPDATE AMS_TIMESLOTS SET RESERVED_COUNT = CAPACITY + 1 "
                    + "WHERE TIMESLOT_ID = ?", Long.valueOf(SLOT_WITH_ROOM));
            throw new AssertionError("an over-capacity write was accepted");
        } catch (final org.springframework.dao.DataAccessException expected) {
            assertTrue("expected a constraint violation, got: " + expected.getMessage(),
                    expected.getMessage().contains("TIMESLOTS_RESERVED_CK")
                            || expected.getMessage().contains("23513"));
        }
    }

    private int reservedCount(final long timeslotId) {
        return jdbcTemplate.queryForObject(
                "SELECT NVL(RESERVED_COUNT, 0) FROM AMS_TIMESLOTS WHERE TIMESLOT_ID = ?",
                Integer.class, Long.valueOf(timeslotId)).intValue();
    }

    private String availableFlag(final long timeslotId) {
        return jdbcTemplate.queryForObject(
                "SELECT AVAILABLE_FL FROM AMS_TIMESLOTS WHERE TIMESLOT_ID = ?",
                String.class, Long.valueOf(timeslotId));
    }

    private String orderStatus(final long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT ORDER_STATUS_CD FROM AMS_ORDERS WHERE ORDER_ID = ?",
                String.class, Long.valueOf(orderId));
    }

    private int heldRows(final long timeslotId, final String entityType, final long entityId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM AMS_TIMESLOT_RESERVATIONS WHERE TIMESLOT_ID = ? "
                + "AND ENTITY_TYPE_CD = ? AND ENTITY_ID = ? AND RESERVATION_STATUS_CD = 'HELD'",
                Integer.class, Long.valueOf(timeslotId), entityType, Long.valueOf(entityId))
                .intValue();
    }
}
