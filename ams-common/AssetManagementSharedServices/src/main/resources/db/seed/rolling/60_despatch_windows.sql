-- Despatch windows for the last step of the ordering flow.
--
-- Rolling, and re-run on every startup. The Oracle original generated these once, at container
-- first boot, relative to SYSDATE - which meant that on a database more than three weeks old the
-- despatch step was silently empty. An embedded database that persists between restarts makes that
-- worse, not better, so this tier is deliberately re-applied each time.
--
-- Only future slots that nobody holds are cleared, so refreshing never strands a window an order is
-- relying on. The NOT IN is safe here only because TIMESLOT_ID is NOT NULL in the ledger.
DELETE FROM AMS_TIMESLOTS
 WHERE CALL_TYPE_CD = 'SHIP'
   AND START_TM > SYSTIMESTAMP
   AND TIMESLOT_ID NOT IN (SELECT TIMESLOT_ID FROM AMS_TIMESLOT_RESERVATIONS);

-- Set-based rather than the PL/SQL loop it replaces. Two H2 details make this look different from
-- the Oracle version: date arithmetic needs DATEADD (TRUNC(SYSDATE) + X raises "BIGINT + DATE"),
-- and DAY_OF_WEEK numbers Sunday as 1 and Saturday as 7 - so the weekend skip is NOT IN (1, 7)
-- rather than a TO_CHAR comparison against 'SAT' and 'SUN'. Nothing despatches at a weekend, and
-- offering those windows would book a van that does not run.
INSERT INTO AMS_TIMESLOTS
       (TIMESLOT_ID, CALL_TYPE_CD, REGION_CD, START_TM, END_TM,
        CAPACITY, RESERVED_COUNT, AVAILABLE_FL, DISPLAY_LABEL, TIME_ZONE)
SELECT AMS_TIMESLOTS_SQ.NEXTVAL, 'SHIP', REG.REGION_CD,
       DATEADD('HOUR',  8, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       DATEADD('HOUR', 12, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       6, 0, 'Y', 'Morning despatch (8:00 AM - 12:00 PM)', 'America/New_York'
  FROM SYSTEM_RANGE(2, 21) D,
       (SELECT DISTINCT REGION_CD FROM AMS_INSTALL_REGIONS) REG
 WHERE DAY_OF_WEEK(DATEADD('DAY', D.X, TRUNC(SYSDATE))) NOT IN (1, 7);

-- The afternoon van is smaller because the second run of the day is the one that overruns.
INSERT INTO AMS_TIMESLOTS
       (TIMESLOT_ID, CALL_TYPE_CD, REGION_CD, START_TM, END_TM,
        CAPACITY, RESERVED_COUNT, AVAILABLE_FL, DISPLAY_LABEL, TIME_ZONE)
SELECT AMS_TIMESLOTS_SQ.NEXTVAL, 'SHIP', REG.REGION_CD,
       DATEADD('HOUR', 13, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       DATEADD('HOUR', 17, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       4, 0, 'Y', 'Afternoon despatch (1:00 PM - 5:00 PM)', 'America/New_York'
  FROM SYSTEM_RANGE(2, 21) D,
       (SELECT DISTINCT REGION_CD FROM AMS_INSTALL_REGIONS) REG
 WHERE DAY_OF_WEEK(DATEADD('DAY', D.X, TRUNC(SYSDATE))) NOT IN (1, 7);
