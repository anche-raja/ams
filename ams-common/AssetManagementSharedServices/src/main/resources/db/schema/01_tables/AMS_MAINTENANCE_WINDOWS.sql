-- AMS_MAINTENANCE_WINDOWS
-- Generated from the H2 test fixture; edit that and re-run the translator.
CREATE TABLE IF NOT EXISTS AMS_MAINTENANCE_WINDOWS (
  MAINT_WINDOW_ID  NUMBER(19) NOT NULL,
  -- Exactly one of these is set. ASSET_ID for a window edited against live hardware; ORDER_ID
  -- for one chosen while ordering, when the asset does not exist yet. The window is copied onto
  -- the asset at installation, which is when the pair briefly overlaps.
  ASSET_ID         NUMBER(19),
  ORDER_ID         NUMBER(19),
  DAY_CD           VARCHAR2(10 CHAR),
  START_HOUR_CD    VARCHAR2(10 CHAR),
  END_HOUR_CD      VARCHAR2(10 CHAR),
  TIME_ZONE        VARCHAR2(40 CHAR),
  ENABLED_FL       VARCHAR2(1 CHAR) DEFAULT 'Y',
  CREATED_DT       TIMESTAMP,
  CREATED_BY       VARCHAR2(64 CHAR),
  MODIFIED_DT      TIMESTAMP,
  MODIFIED_BY      VARCHAR2(64 CHAR),
  CONSTRAINT AMS_MAINTENANCE_WINDOWS_PK PRIMARY KEY (MAINT_WINDOW_ID)
);
