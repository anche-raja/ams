-- AMS_TIMESLOT_RESERVATIONS
--
-- The ledger behind AMS_TIMESLOTS.RESERVED_COUNT.
--
-- The counter on its own records how many places are taken but not who holds them, which leaves
-- cancel_timeslot no way to tell whether the caller actually has a reservation. It would have to
-- decrement blind, so a double-cancel drives the count below true usage and the last place gets
-- handed out twice. One row per reservation makes both reserve and cancel idempotent: reserving
-- twice returns OK without taking a second place, cancelling something not held returns
-- NOT_RESERVED and touches nothing.
--
-- Not part of the H2 test schema: nothing in Java reads this table, only the PL/SQL does.
CREATE TABLE IF NOT EXISTS AMS_TIMESLOT_RESERVATIONS (
  RESERVATION_ID         NUMBER(19) NOT NULL,
  TIMESLOT_ID            NUMBER(19) NOT NULL,
  ENTITY_TYPE_CD         VARCHAR2(20 CHAR) NOT NULL,
  ENTITY_ID              NUMBER(19) NOT NULL,
  SCHEDULED_DT           TIMESTAMP,
  RESERVATION_STATUS_CD  VARCHAR2(20 CHAR) DEFAULT 'HELD' NOT NULL,
  RELEASE_REASON         VARCHAR2(400 CHAR),
  RELEASED_DT            TIMESTAMP,
  CREATED_DT             TIMESTAMP,
  CREATED_BY             VARCHAR2(64 CHAR),
  MODIFIED_DT            TIMESTAMP,
  MODIFIED_BY            VARCHAR2(64 CHAR),
  CONSTRAINT AMS_TIMESLOT_RESERVATIONS_PK PRIMARY KEY (RESERVATION_ID)
);
