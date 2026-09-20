-- AMS_ASSET_CONFIGS
-- Generated from the H2 test fixture; edit that and re-run the translator.
CREATE TABLE IF NOT EXISTS AMS_ASSET_CONFIGS (
  CONFIG_ID          NUMBER(19) NOT NULL,
  -- ASSET_ID is null for the revision built while ordering: the device is not created until it
  -- is despatched, but the warehouse needs its configuration before that.
  ASSET_ID           NUMBER(19),
  ORDER_ID           NUMBER(19),
  CONFIG_TYPE_CD     VARCHAR2(20 CHAR),
  CONFIG_STATUS_CD   VARCHAR2(20 CHAR),
  NETWORK_CONFIG_CD  VARCHAR2(20 CHAR),
  DATA_SOURCE_CD     VARCHAR2(20 CHAR) DEFAULT 'AMS',
  LAN_IP_ADDRESS     VARCHAR2(15 CHAR),
  LAN_SUBNET_MASK    VARCHAR2(15 CHAR),
  WAN_IP_ADDRESS     VARCHAR2(15 CHAR),
  WAN_SUBNET_MASK    VARCHAR2(15 CHAR),
  -- The carrier's next hop, on the WAN side.
  DEFAULT_GATEWAY    VARCHAR2(15 CHAR),
  -- The customer's own router inside the site. A different device from the one being
  -- ordered, which is why it is not derivable from LAN_IP_ADDRESS.
  LAN_GATEWAY        VARCHAR2(15 CHAR),
  PRIMARY_DNS        VARCHAR2(15 CHAR),
  SECONDARY_DNS      VARCHAR2(15 CHAR),
  CIRCUIT_ID         VARCHAR2(60 CHAR),
  BANDWIDTH_KBPS     NUMBER(10),
  EFFECTIVE_DT       TIMESTAMP,
  REVISION_NUM       NUMBER(10),
  CREATED_DT         TIMESTAMP,
  CREATED_BY         VARCHAR2(64 CHAR),
  MODIFIED_DT        TIMESTAMP,
  MODIFIED_BY        VARCHAR2(64 CHAR),
  CONSTRAINT AMS_ASSET_CONFIGS_PK PRIMARY KEY (CONFIG_ID)
);
