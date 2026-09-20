-- AMS_CIRCUIT_WINDOWS
--
-- What makes a site type change different from every other network change request.
--
-- A site type change reshapes the circuit as well as the device, and the carrier accepts only a
-- bounded number of circuit changes per region per night. Booking one therefore has to take
-- capacity on a second calendar, which is why schedule_site_type_ncr is a separate procedure
-- rather than a flag on schedule_ncr. This table is the ledger for those bookings, and it is what
-- p_circuit_window_id and p_released_count refer to - both are declared by the Java today but had
-- nothing behind them.
--
-- Capacity itself lives in AMS_TIMESLOTS under CALL_TYPE_CD = 'CIRCUIT' rather than in a second
-- capacity table: the FOR UPDATE decrement there is already proven, and these rows cannot leak
-- into the UI because FacilitationCallType has no CIRCUIT constant for any Java query to ask for.
CREATE TABLE IF NOT EXISTS AMS_CIRCUIT_WINDOWS (
  CIRCUIT_WINDOW_ID  NUMBER(19) NOT NULL,
  NCR_ID             NUMBER(19) NOT NULL,
  ASSET_ID           NUMBER(19) NOT NULL,
  TIMESLOT_ID        NUMBER(19) NOT NULL,
  CIRCUIT_ID         VARCHAR2(60 CHAR),
  SITE_TYPE_CD       VARCHAR2(20 CHAR),
  WINDOW_DT          TIMESTAMP NOT NULL,
  WINDOW_STATUS_CD   VARCHAR2(20 CHAR) DEFAULT 'RESERVED' NOT NULL,
  RELEASE_REASON     VARCHAR2(400 CHAR),
  RELEASED_DT        TIMESTAMP,
  CREATED_DT         TIMESTAMP,
  CREATED_BY         VARCHAR2(64 CHAR),
  MODIFIED_DT        TIMESTAMP,
  MODIFIED_BY        VARCHAR2(64 CHAR),
  CONSTRAINT AMS_CIRCUIT_WINDOWS_PK PRIMARY KEY (CIRCUIT_WINDOW_ID)
);
