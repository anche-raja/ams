CREATE OR REPLACE PACKAGE BODY AMS_SCHEDULING_PG AS

  -- Raised when FOR UPDATE WAIT n expires. Caught and mapped to a status, never allowed to escape.
  resource_busy EXCEPTION;
  PRAGMA EXCEPTION_INIT(resource_busy, -30006);
  lock_nowait   EXCEPTION;
  PRAGMA EXCEPTION_INIT(lock_nowait, -54);

  /*
   * How long to wait for a row lock.
   *
   * NOWAIT would be wrong: the lock is held for a single-row update measured in microseconds, and
   * a brief overlap under normal load would be reported to the user as "that slot was just taken"
   * when it was not.
   *
   * Five seconds rather than longer because the connection pool's wait timeout (30 seconds) bounds
   * waiting for a connection from the pool, NOT waiting for a query. A session blocked on a row
   * lock is holding its connection, so a long lock wait converts row contention into pool
   * exhaustion across all 50 connections and unrelated requests start failing. There is no
   * statement timeout configured anywhere, so this clause is the only bound that exists.
   */
  C_LOCK_WAIT CONSTANT PLS_INTEGER := 5;

  C_DEFAULT_MAX_DECOM_DAYS CONSTANT PLS_INTEGER := 42;

  -- ------------------------------------------------------------------
  -- helpers
  -- ------------------------------------------------------------------

  FUNCTION get_number_property(p_key IN VARCHAR2, p_default IN NUMBER) RETURN NUMBER IS
    l_value AMS_PROPERTIES.PROPERTY_VALUE%TYPE;
  BEGIN
    SELECT PROPERTY_VALUE INTO l_value FROM AMS_PROPERTIES WHERE PROPERTY_KEY = p_key;
    RETURN TO_NUMBER(TRIM(l_value));
  EXCEPTION
    -- Unset or unparseable both fall back, matching ConfigServiceImpl.getInt on the Java side.
    WHEN NO_DATA_FOUND THEN RETURN p_default;
    WHEN VALUE_ERROR   THEN RETURN p_default;
    WHEN INVALID_NUMBER THEN RETURN p_default;
  END get_number_property;

  -- ------------------------------------------------------------------
  -- reserve_timeslot
  -- ------------------------------------------------------------------
  PROCEDURE reserve_timeslot(p_timeslot_id    IN  NUMBER,
                             p_entity_id      IN  NUMBER,
                             p_entity_type_cd IN  VARCHAR2,
                             p_scheduled_dt   IN  TIMESTAMP,
                             p_user_id        IN  VARCHAR2,
                             p_status_cd      OUT VARCHAR2,
                             p_message        OUT VARCHAR2) IS
    l_capacity     AMS_TIMESLOTS.CAPACITY%TYPE;
    l_reserved     AMS_TIMESLOTS.RESERVED_COUNT%TYPE;
    l_available    AMS_TIMESLOTS.AVAILABLE_FL%TYPE;
    l_start_tm     AMS_TIMESLOTS.START_TM%TYPE;
    l_existing     NUMBER;
    l_type         VARCHAR2(20 CHAR) := UPPER(TRIM(p_entity_type_cd));
    l_rows         PLS_INTEGER;
  BEGIN
    -- Assigned before anything can fail. Oracle does not copy OUT parameters back on an unhandled
    -- exception, so an unassigned OUT would read as null in Java.
    p_status_cd := C_ERROR;
    p_message   := NULL;

    IF NVL(p_timeslot_id, 0) = 0 OR NVL(p_entity_id, 0) = 0
       OR l_type NOT IN ('INSTALL', 'TECHLINE', 'NCR', 'SHIP') THEN
      p_status_cd := C_INVALID_INPUT;
      p_message   := 'A timeslot, an entity and a valid entity type are all required';
      RETURN;
    END IF;

    SAVEPOINT ams_reserve_ts;

    BEGIN
      SELECT CAPACITY, RESERVED_COUNT, AVAILABLE_FL, START_TM
        INTO l_capacity, l_reserved, l_available, l_start_tm
        FROM AMS_TIMESLOTS
       WHERE TIMESLOT_ID = p_timeslot_id
         FOR UPDATE WAIT 5;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        p_status_cd := C_NOT_FOUND;
        p_message   := 'That timeslot no longer exists';
        RETURN;
      WHEN resource_busy OR lock_nowait THEN
        p_status_cd := C_BUSY;
        p_message   := 'Someone else is booking that timeslot; please try again';
        RETURN;
    END;

    IF NVL(l_available, 'N') <> 'Y' THEN
      p_status_cd := C_SLOT_CLOSED;
      p_message   := 'That timeslot is not open for booking';
      RETURN;
    END IF;

    -- Already held by this entity? Return success without taking a second place. A resubmitted
    -- form must not error, and must not consume capacity twice.
    SELECT COUNT(*) INTO l_existing
      FROM AMS_TIMESLOT_RESERVATIONS
     WHERE TIMESLOT_ID = p_timeslot_id
       AND ENTITY_TYPE_CD = l_type
       AND ENTITY_ID = p_entity_id
       AND RESERVATION_STATUS_CD = 'HELD';

    IF l_existing > 0 THEN
      p_status_cd := C_OK;
      p_message   := 'Already reserved';
      RETURN;
    END IF;

    IF NVL(l_reserved, 0) >= NVL(l_capacity, 0) THEN
      p_status_cd := C_NO_CAPACITY;
      p_message   := 'That timeslot is full';
      RETURN;
    END IF;

    -- AVAILABLE_FL is deliberately not touched. It means "ops opened this slot", not "this slot
    -- has room" - fullness is RESERVED_COUNT >= CAPACITY, which both calendar DAOs already test.
    -- Flipping it on filling would let a later cancel re-open a slot ops had closed by hand.
    UPDATE AMS_TIMESLOTS
       SET RESERVED_COUNT = NVL(RESERVED_COUNT, 0) + 1
     WHERE TIMESLOT_ID = p_timeslot_id;

    INSERT INTO AMS_TIMESLOT_RESERVATIONS
           (RESERVATION_ID, TIMESLOT_ID, ENTITY_TYPE_CD, ENTITY_ID, SCHEDULED_DT,
            RESERVATION_STATUS_CD, CREATED_DT, CREATED_BY, MODIFIED_DT, MODIFIED_BY)
    VALUES (AMS_TIMESLOT_RESERVATIONS_SQ.NEXTVAL, p_timeslot_id, l_type, p_entity_id,
            NVL(p_scheduled_dt, l_start_tm), 'HELD',
            SYSTIMESTAMP, p_user_id, SYSTIMESTAMP, p_user_id);

    IF l_type = 'INSTALL' THEN
      -- p_entity_id is the order id here, not the installation id.
      UPDATE AMS_INSTALLATIONS
         SET TIMESLOT_ID = p_timeslot_id,
             SCHEDULED_DT = NVL(p_scheduled_dt, l_start_tm),
             INSTALL_STATUS_CD = CASE WHEN INSTALL_STATUS_CD IN ('SCHEDULED', 'RESCHED')
                                      THEN 'RESCHED' ELSE 'SCHEDULED' END,
             MODIFIED_DT = SYSTIMESTAMP,
             MODIFIED_BY = p_user_id
       WHERE ORDER_ID = p_entity_id;
      l_rows := SQL%ROWCOUNT;

      IF l_rows = 0 THEN
        -- Booking an engineer for an order with no installation record would strand the capacity.
        ROLLBACK TO ams_reserve_ts;
        p_status_cd := C_NO_INSTALLATION;
        p_message   := 'That order has no installation to schedule';
        RETURN;
      END IF;

      UPDATE AMS_ORDERS
         SET ORDER_STATUS_CD = 'SCHEDULED',
             MODIFIED_DT = SYSTIMESTAMP,
             MODIFIED_BY = p_user_id
       WHERE ORDER_ID = p_entity_id
         AND ORDER_STATUS_CD NOT IN ('CANCELLED', 'COMPLETED');
    END IF;

    -- TECHLINE has no entity table of its own, and for NCR this is the move timeslot rather than
    -- the change date, so neither needs a further update. The ledger row is the record.
    --
    -- SHIP is deliberately in the same position. The despatch window is written onto
    -- AMS_ORDERS.SHIP_WINDOW_ID by the same transaction that inserts the order, and unlike
    -- INSTALL it must NOT move the order to 'SCHEDULED': a window means the warehouse will pick
    -- it, not that an engineer has been booked.

    p_status_cd := C_OK;
  EXCEPTION
    WHEN OTHERS THEN
      ROLLBACK TO ams_reserve_ts;
      RAISE;
  END reserve_timeslot;

  -- ------------------------------------------------------------------
  -- cancel_timeslot
  -- ------------------------------------------------------------------
  PROCEDURE cancel_timeslot(p_timeslot_id    IN  NUMBER,
                            p_entity_id      IN  NUMBER,
                            p_entity_type_cd IN  VARCHAR2,
                            p_user_id        IN  VARCHAR2,
                            p_status_cd      OUT VARCHAR2) IS
    l_dummy          NUMBER;
    l_reservation_id AMS_TIMESLOT_RESERVATIONS.RESERVATION_ID%TYPE;
    l_type           VARCHAR2(20 CHAR) := UPPER(TRIM(p_entity_type_cd));
  BEGIN
    p_status_cd := C_ERROR;

    IF NVL(p_timeslot_id, 0) = 0 OR NVL(p_entity_id, 0) = 0 OR l_type IS NULL THEN
      p_status_cd := C_INVALID_INPUT;
      RETURN;
    END IF;

    SAVEPOINT ams_cancel_ts;

    BEGIN
      SELECT TIMESLOT_ID INTO l_dummy
        FROM AMS_TIMESLOTS
       WHERE TIMESLOT_ID = p_timeslot_id
         FOR UPDATE WAIT 5;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN p_status_cd := C_NOT_FOUND; RETURN;
      WHEN resource_busy OR lock_nowait THEN p_status_cd := C_BUSY; RETURN;
    END;

    BEGIN
      SELECT RESERVATION_ID INTO l_reservation_id
        FROM AMS_TIMESLOT_RESERVATIONS
       WHERE TIMESLOT_ID = p_timeslot_id
         AND ENTITY_TYPE_CD = l_type
         AND ENTITY_ID = p_entity_id
         AND RESERVATION_STATUS_CD = 'HELD'
         FOR UPDATE WAIT 5;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        -- Nothing held, so nothing to give back. This is what makes a double-cancel harmless:
        -- without the ledger we would decrement blind and eventually hand out a place twice.
        p_status_cd := C_NOT_RESERVED;
        RETURN;
      WHEN resource_busy OR lock_nowait THEN
        p_status_cd := C_BUSY;
        RETURN;
    END;

    UPDATE AMS_TIMESLOT_RESERVATIONS
       SET RESERVATION_STATUS_CD = 'RELEASED',
           RELEASED_DT = SYSTIMESTAMP,
           MODIFIED_DT = SYSTIMESTAMP,
           MODIFIED_BY = p_user_id
     WHERE RESERVATION_ID = l_reservation_id;

    -- GREATEST floors this at zero. The check constraint would catch a negative anyway, but an
    -- ORA-02290 in front of a user is a worse outcome than a clamped count.
    UPDATE AMS_TIMESLOTS
       SET RESERVED_COUNT = GREATEST(NVL(RESERVED_COUNT, 0) - 1, 0)
     WHERE TIMESLOT_ID = p_timeslot_id;

    IF l_type = 'INSTALL' THEN
      UPDATE AMS_INSTALLATIONS
         SET TIMESLOT_ID = NULL,
             SCHEDULED_DT = NULL,
             INSTALL_STATUS_CD = 'NOTSCHED',
             MODIFIED_DT = SYSTIMESTAMP,
             MODIFIED_BY = p_user_id
       WHERE ORDER_ID = p_entity_id
         AND TIMESLOT_ID = p_timeslot_id;
    END IF;

    p_status_cd := C_OK;
  EXCEPTION
    WHEN OTHERS THEN
      ROLLBACK TO ams_cancel_ts;
      RAISE;
  END cancel_timeslot;

  -- ------------------------------------------------------------------
  -- schedule_decommission
  -- ------------------------------------------------------------------
  PROCEDURE schedule_decommission(p_decommission_id    IN  NUMBER,
                                  p_asset_id           IN  NUMBER,
                                  p_scheduled_dt       IN  TIMESTAMP,
                                  p_hardware_return_fl IN  VARCHAR2,
                                  p_user_id            IN  VARCHAR2,
                                  p_status_cd          OUT VARCHAR2) IS
    l_decom_id   AMS_DECOMMISSIONS.DECOMMISSION_ID%TYPE;
    l_status     AMS_DECOMMISSIONS.DECOM_STATUS_CD%TYPE;
    l_asset_id   AMS_DECOMMISSIONS.ASSET_ID%TYPE;
    l_asset_stat AMS_ASSETS.ASSET_STATUS_CD%TYPE;
    l_flag       VARCHAR2(1 CHAR) := UPPER(NVL(TRIM(p_hardware_return_fl), 'N'));
    l_max_days   NUMBER;
  BEGIN
    p_status_cd := C_ERROR;

    IF NVL(p_asset_id, 0) = 0 OR p_scheduled_dt IS NULL OR l_flag NOT IN ('Y', 'N') THEN
      p_status_cd := C_INVALID_INPUT;
      RETURN;
    END IF;

    SAVEPOINT ams_sched_decom;

    BEGIN
      SELECT ASSET_STATUS_CD INTO l_asset_stat
        FROM AMS_ASSETS WHERE ASSET_ID = p_asset_id;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN p_status_cd := C_NOT_FOUND; RETURN;
    END;

    IF l_asset_stat IN ('DECOM', 'CANCELLED') THEN
      p_status_cd := C_NOT_OPEN;
      RETURN;
    END IF;

    -- Date gates. CalendarServiceImpl already refuses these before calling, so reaching them means
    -- a caller that bypassed the service - hence a status rather than an exception.
    l_max_days := get_number_property('MAXDECOMDAYS', C_DEFAULT_MAX_DECOM_DAYS);
    IF TRUNC(CAST(p_scheduled_dt AS DATE)) < TRUNC(SYSDATE) THEN
      p_status_cd := C_DATE_IN_PAST;
      RETURN;
    END IF;
    IF TRUNC(CAST(p_scheduled_dt AS DATE)) > TRUNC(SYSDATE) + l_max_days THEN
      p_status_cd := C_OUTSIDE_WINDOW;
      RETURN;
    END IF;

    -- CalendarServiceImpl passes 0 where the Java value was null.
    IF NVL(p_decommission_id, 0) = 0 THEN
      -- Two steps on purpose. Oracle rejects FOR UPDATE against an inline view that carries an
      -- ORDER BY (ORA-02014), so the newest open decommission is identified first and locked by
      -- id second. Status and asset are re-read under the lock, so nothing is trusted from the
      -- unlocked read.
      BEGIN
        SELECT DECOMMISSION_ID INTO l_decom_id
          FROM (SELECT DECOMMISSION_ID
                  FROM AMS_DECOMMISSIONS
                 WHERE ASSET_ID = p_asset_id
                   AND DECOM_STATUS_CD IN ('REQUESTED', 'SCHEDULED')
                 ORDER BY REQUESTED_DT DESC, DECOMMISSION_ID DESC)
         WHERE ROWNUM <= 1;
      EXCEPTION
        WHEN NO_DATA_FOUND THEN
          l_decom_id := NULL;
      END;

      IF l_decom_id IS NULL THEN
        l_decom_id := AMS_DECOMMISSIONS_SQ.NEXTVAL;
        INSERT INTO AMS_DECOMMISSIONS
               (DECOMMISSION_ID, ASSET_ID, DECOM_STATUS_CD, REQUESTED_DT,
                HARDWARE_RETURN_FL, CREATED_DT, CREATED_BY, MODIFIED_DT, MODIFIED_BY)
        VALUES (l_decom_id, p_asset_id, 'REQUESTED', SYSTIMESTAMP,
                l_flag, SYSTIMESTAMP, p_user_id, SYSTIMESTAMP, p_user_id);
        l_status   := 'REQUESTED';
        l_asset_id := p_asset_id;
      ELSE
        BEGIN
          SELECT DECOM_STATUS_CD, ASSET_ID INTO l_status, l_asset_id
            FROM AMS_DECOMMISSIONS
           WHERE DECOMMISSION_ID = l_decom_id
             FOR UPDATE WAIT 5;
        EXCEPTION
          WHEN NO_DATA_FOUND THEN p_status_cd := C_NOT_FOUND; RETURN;
          WHEN resource_busy OR lock_nowait THEN p_status_cd := C_BUSY; RETURN;
        END;
      END IF;
    ELSE
      BEGIN
        SELECT DECOMMISSION_ID, DECOM_STATUS_CD, ASSET_ID
          INTO l_decom_id, l_status, l_asset_id
          FROM AMS_DECOMMISSIONS
         WHERE DECOMMISSION_ID = p_decommission_id
           FOR UPDATE WAIT 5;
      EXCEPTION
        WHEN NO_DATA_FOUND THEN p_status_cd := C_NOT_FOUND; RETURN;
        WHEN resource_busy OR lock_nowait THEN p_status_cd := C_BUSY; RETURN;
      END;
    END IF;

    IF l_status IN ('COMPLETED', 'CANCELLED') THEN
      ROLLBACK TO ams_sched_decom;
      p_status_cd := C_NOT_OPEN;
      RETURN;
    END IF;
    IF l_status = 'INPROG' THEN
      ROLLBACK TO ams_sched_decom;
      p_status_cd := C_IN_PROGRESS;
      RETURN;
    END IF;
    IF l_asset_id <> p_asset_id THEN
      ROLLBACK TO ams_sched_decom;
      p_status_cd := C_ASSET_MISMATCH;
      RETURN;
    END IF;

    UPDATE AMS_DECOMMISSIONS
       SET SCHEDULED_DT = p_scheduled_dt,
           DECOM_STATUS_CD = 'SCHEDULED',
           HARDWARE_RETURN_FL = l_flag,
           MODIFIED_DT = SYSTIMESTAMP,
           MODIFIED_BY = p_user_id
     WHERE DECOMMISSION_ID = l_decom_id;

    UPDATE AMS_ASSETS
       SET ASSET_STATUS_CD = 'PENDDECOM',
           MODIFIED_DT = SYSTIMESTAMP,
           MODIFIED_BY = p_user_id
     WHERE ASSET_ID = p_asset_id
       AND ASSET_STATUS_CD IN ('ACTIVE', 'INSTALLED');

    p_status_cd := C_OK;
  EXCEPTION
    WHEN OTHERS THEN
      ROLLBACK TO ams_sched_decom;
      RAISE;
  END schedule_decommission;

  -- ------------------------------------------------------------------
  -- cancel_decommission
  -- ------------------------------------------------------------------
  PROCEDURE cancel_decommission(p_decommission_id IN  NUMBER,
                                p_reason          IN  VARCHAR2,
                                p_user_id         IN  VARCHAR2,
                                p_status_cd       OUT VARCHAR2) IS
    l_status    AMS_DECOMMISSIONS.DECOM_STATUS_CD%TYPE;
    l_sched     AMS_DECOMMISSIONS.SCHEDULED_DT%TYPE;
    l_asset_id  AMS_DECOMMISSIONS.ASSET_ID%TYPE;
    l_others    NUMBER;
  BEGIN
    p_status_cd := C_ERROR;

    IF NVL(p_decommission_id, 0) = 0 THEN
      p_status_cd := C_INVALID_INPUT;
      RETURN;
    END IF;

    SAVEPOINT ams_cancel_decom;

    BEGIN
      SELECT DECOM_STATUS_CD, SCHEDULED_DT, ASSET_ID
        INTO l_status, l_sched, l_asset_id
        FROM AMS_DECOMMISSIONS
       WHERE DECOMMISSION_ID = p_decommission_id
         FOR UPDATE WAIT 5;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN p_status_cd := C_NOT_FOUND; RETURN;
      WHEN resource_busy OR lock_nowait THEN p_status_cd := C_BUSY; RETURN;
    END;

    IF l_status IN ('COMPLETED', 'CANCELLED') THEN
      p_status_cd := C_NOT_OPEN;
      RETURN;
    END IF;
    IF l_status = 'INPROG' THEN
      p_status_cd := C_IN_PROGRESS;
      RETURN;
    END IF;
    IF l_sched IS NULL THEN
      p_status_cd := C_NOT_SCHEDULED;
      RETURN;
    END IF;

    -- Back to REQUESTED, never to CANCELLED: this releases the date, not the request.
    UPDATE AMS_DECOMMISSIONS
       SET SCHEDULED_DT = NULL,
           DECOM_STATUS_CD = 'REQUESTED',
           REASON = SUBSTR(p_reason, 1, 400),
           MODIFIED_DT = SYSTIMESTAMP,
           MODIFIED_BY = p_user_id
     WHERE DECOMMISSION_ID = p_decommission_id;

    -- Only put the asset back if nothing else still has it pending decommission.
    SELECT COUNT(*) INTO l_others
      FROM AMS_DECOMMISSIONS
     WHERE ASSET_ID = l_asset_id
       AND DECOMMISSION_ID <> p_decommission_id
       AND DECOM_STATUS_CD = 'SCHEDULED';

    IF l_others = 0 THEN
      UPDATE AMS_ASSETS
         SET ASSET_STATUS_CD = 'ACTIVE',
             MODIFIED_DT = SYSTIMESTAMP,
             MODIFIED_BY = p_user_id
       WHERE ASSET_ID = l_asset_id
         AND ASSET_STATUS_CD = 'PENDDECOM';
    END IF;

    p_status_cd := C_OK;
  EXCEPTION
    WHEN OTHERS THEN
      ROLLBACK TO ams_cancel_decom;
      RAISE;
  END cancel_decommission;

END AMS_SCHEDULING_PG;
/
