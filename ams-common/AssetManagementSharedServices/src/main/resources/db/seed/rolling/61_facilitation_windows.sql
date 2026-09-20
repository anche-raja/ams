-- Installation and tech-line capacity, on the same rolling basis as the despatch windows.
--
-- Without these an order can be placed but never scheduled: the calendar screens only offer future
-- slots, and the four fixed slots in 14_AMS_TIMESLOTS are dated 2025 and exist for the DAO tests,
-- which query an explicit range and would assert nothing against a moving date.
DELETE FROM AMS_TIMESLOTS
 WHERE CALL_TYPE_CD IN ('INSTALL', 'TECHLINE', 'CIRCUIT')
   AND START_TM > SYSTIMESTAMP
   AND TIMESLOT_ID NOT IN (SELECT TIMESLOT_ID FROM AMS_TIMESLOT_RESERVATIONS);

-- Installations run as half-day visits; the afternoon slot is smaller for the same reason as the
-- afternoon van.
INSERT INTO AMS_TIMESLOTS
       (TIMESLOT_ID, CALL_TYPE_CD, REGION_CD, START_TM, END_TM,
        CAPACITY, RESERVED_COUNT, AVAILABLE_FL, DISPLAY_LABEL, TIME_ZONE)
SELECT AMS_TIMESLOTS_SQ.NEXTVAL, 'INSTALL', REG.REGION_CD,
       DATEADD('HOUR',  8, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       DATEADD('HOUR', 12, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       3, 0, 'Y', 'Morning visit (8:00 AM - 12:00 PM)', 'America/New_York'
  FROM SYSTEM_RANGE(3, 24) D,
       (SELECT DISTINCT REGION_CD FROM AMS_INSTALL_REGIONS) REG
 WHERE DAY_OF_WEEK(DATEADD('DAY', D.X, TRUNC(SYSDATE))) NOT IN (1, 7);

INSERT INTO AMS_TIMESLOTS
       (TIMESLOT_ID, CALL_TYPE_CD, REGION_CD, START_TM, END_TM,
        CAPACITY, RESERVED_COUNT, AVAILABLE_FL, DISPLAY_LABEL, TIME_ZONE)
SELECT AMS_TIMESLOTS_SQ.NEXTVAL, 'INSTALL', REG.REGION_CD,
       DATEADD('HOUR', 13, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       DATEADD('HOUR', 17, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       2, 0, 'Y', 'Afternoon visit (1:00 PM - 5:00 PM)', 'America/New_York'
  FROM SYSTEM_RANGE(3, 24) D,
       (SELECT DISTINCT REGION_CD FROM AMS_INSTALL_REGIONS) REG
 WHERE DAY_OF_WEEK(DATEADD('DAY', D.X, TRUNC(SYSDATE))) NOT IN (1, 7);

-- Tech line is a phone appointment, so it is shorter and more of them fit.
INSERT INTO AMS_TIMESLOTS
       (TIMESLOT_ID, CALL_TYPE_CD, REGION_CD, START_TM, END_TM,
        CAPACITY, RESERVED_COUNT, AVAILABLE_FL, DISPLAY_LABEL, TIME_ZONE)
SELECT AMS_TIMESLOTS_SQ.NEXTVAL, 'TECHLINE', REG.REGION_CD,
       DATEADD('HOUR',  9, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       DATEADD('HOUR', 11, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       4, 0, 'Y', '9:00 AM - 11:00 AM', 'America/New_York'
  FROM SYSTEM_RANGE(3, 24) D,
       (SELECT DISTINCT REGION_CD FROM AMS_INSTALL_REGIONS) REG
 WHERE DAY_OF_WEEK(DATEADD('DAY', D.X, TRUNC(SYSDATE))) NOT IN (1, 7);

-- Circuit capacity, which the site-type change path books against. Region 'ALL' is the fallback
-- resolve_region used when an asset's address does not map to a region, so it needs windows too.
INSERT INTO AMS_TIMESLOTS
       (TIMESLOT_ID, CALL_TYPE_CD, REGION_CD, START_TM, END_TM,
        CAPACITY, RESERVED_COUNT, AVAILABLE_FL, DISPLAY_LABEL, TIME_ZONE)
SELECT AMS_TIMESLOTS_SQ.NEXTVAL, 'CIRCUIT', REG.REGION_CD,
       DATEADD('HOUR', 0, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       DATEADD('HOUR', 6, DATEADD('DAY', D.X, TRUNC(SYSDATE))),
       2, 0, 'Y', 'Overnight circuit window (midnight - 6:00 AM)', 'America/New_York'
  FROM SYSTEM_RANGE(1, 30) D,
       (SELECT REGION_CD FROM AMS_INSTALL_REGIONS
        UNION SELECT 'ALL' FROM DUAL) REG;
