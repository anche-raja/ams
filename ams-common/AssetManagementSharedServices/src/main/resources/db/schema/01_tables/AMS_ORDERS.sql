-- AMS_ORDERS
-- Generated from the H2 test fixture; edit that and re-run the translator.
CREATE TABLE IF NOT EXISTS AMS_ORDERS (
  ORDER_ID              NUMBER(19) NOT NULL,
  ORDER_NUMBER          VARCHAR2(40 CHAR),
  ORDER_TYPE_CD         VARCHAR2(20 CHAR),
  ORDER_STATUS_CD       VARCHAR2(20 CHAR),
  CUSTOMER_ID           NUMBER(19),
  ASSET_ID              NUMBER(19),
  SHIPPING_CARRIER_CD   VARCHAR2(20 CHAR),
  TRACKING_NUMBER       VARCHAR2(60 CHAR),
  SUBMITTED_DT          TIMESTAMP,
  SHIPPED_DT            TIMESTAMP,
  REQUESTED_INSTALL_DT  TIMESTAMP,
  CANCELLED_DT          TIMESTAMP,
  CANCELLATION_REASON   VARCHAR2(400 CHAR),
  CANCEL_PENALTY_FL     VARCHAR2(1 CHAR) DEFAULT 'N',
  COMMENTS              VARCHAR2(2000),
  -- Captured during the ordering flow. All nullable: a saved-for-later order is a partly
  -- populated one, and the steps are completed in order rather than all at once.
  DEVICE_NICKNAME       VARCHAR2(60 CHAR),
  SHIP_ADDRESS_ID       NUMBER(19),
  ORDER_CONTACT_ID      NUMBER(19),
  SHIP_CONTACT_ID       NUMBER(19),
  INSTALL_CONTACT_ID    NUMBER(19),
  MAINT_WINDOW_ID       NUMBER(19),
  CONFIG_ID             NUMBER(19),
  SHIP_WINDOW_ID        NUMBER(19),
  CREATED_DT            TIMESTAMP,
  CREATED_BY            VARCHAR2(64 CHAR),
  MODIFIED_DT           TIMESTAMP,
  MODIFIED_BY           VARCHAR2(64 CHAR),
  CONSTRAINT AMS_ORDERS_PK PRIMARY KEY (ORDER_ID)
);
