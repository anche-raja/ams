-- AMS_SUBSCRIBER_PCS
--
-- The machines that will sit behind the ordered device. Collected during the ordering flow
-- because the LAN has to be sized and addressed before the device is staged: a site of twenty
-- tills needs a different subnet and DHCP pool from a site of two laptops.
--
-- Keyed to the order, not the asset. For a new order there is no asset until despatch, and the
-- list is needed before then; the engineer works from the order.
CREATE TABLE IF NOT EXISTS AMS_SUBSCRIBER_PCS (
  SUBSCRIBER_PC_ID  NUMBER(19) NOT NULL,
  ORDER_ID          NUMBER(19),
  HOST_NAME         VARCHAR2(60 CHAR),
  PC_TYPE_CD        VARCHAR2(20 CHAR),
  OPERATING_SYSTEM  VARCHAR2(60 CHAR),
  MAC_ADDRESS       VARCHAR2(20 CHAR),
  IP_ADDRESS        VARCHAR2(15 CHAR),
  -- 'Y' when IP_ADDRESS is a reservation the engineer must honour rather than a note of what
  -- DHCP happened to hand out.
  STATIC_FL         VARCHAR2(1 CHAR) DEFAULT 'N',
  USER_COUNT        NUMBER(10),
  NOTES             VARCHAR2(400 CHAR),
  CREATED_DT        TIMESTAMP,
  CREATED_BY        VARCHAR2(64 CHAR),
  MODIFIED_DT       TIMESTAMP,
  MODIFIED_BY       VARCHAR2(64 CHAR),
  CONSTRAINT AMS_SUBSCRIBER_PCS_PK PRIMARY KEY (SUBSCRIBER_PC_ID)
);
