INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('MINHRSCANCEL', '48', 'Minimum hours before installation to cancel without penalty');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('MINTECHLEAD', '3', 'Minimum techline lead time in days');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('MININSTLEAD', '10', 'Minimum installation lead time in days');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('MAXDECOMDAYS', '42', 'Maximum decommission scheduling window in days');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('ENABLEIMPERS', 'Y', 'Enable the non-production user impersonation screen');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('ENVNAME', 'FIT', 'Logical environment name');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('NONPRODBANNER', 'You are pointing at the FIT environment', 'Non production banner text');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('ADDRVALON', 'Y', 'Enable outbound address validation');
-- Deliberately not a number: proves the typed accessor falls back rather than throwing.
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('HELPCTRON', 'not-a-flag', 'Malformed on purpose');
-- ADDRVALURL is deliberately absent. It is the address validation endpoint, and there isn't one
-- outside a real deployment: locally the flow uses the canned StubRestService instead, chosen by
-- profile the same way the WebSEAL filter is. Setting a bogus URL here would make every address
-- check a timeout rather than a validation.
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('MINSHIPLEAD', '2', 'Minimum despatch lead time in business days');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('SHIPHORIZON', '21', 'How many days ahead despatch windows are offered');
-- Suggested values for the ordering flow's configuration screen. These are the only parts of a
-- configuration AMS can offer for a brand new customer: the resolvers it runs, the private block it
-- carves site LANs from, and a starting bandwidth. The WAN side is absent by design - it is carrier
-- assigned, WanValidator rejects private and documentation space, and a made up public address
-- would belong to somebody else and would still pass validation.
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('DEFPRIDNS', '9.9.9.9', 'Primary DNS resolver suggested on a new configuration');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('DEFSECDNS', '149.112.112.112', 'Secondary DNS resolver suggested on a new configuration');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('DEFBANDWIDTH', '100000', 'Bandwidth in Kbps suggested when the service does not imply one');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('LANSUGBLOCK', '192.168.0.0', 'Private /16 the suggested site LAN is carved out of');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('SUBSTATSTART', '11', 'First host number handed to a static subscriber machine');
-- The WAN pool a customer's first site is numbered from. A working default for development:
-- point it at the range you actually hold. WanValidator refuses private and documentation space,
-- so this has to be routable public space or the values it produces will not pass the screen.
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('DEFWANSUBNET', '198.51.45.0', 'Network address of the WAN pool a first site is numbered from');
INSERT INTO AMS_PROPERTIES (PROPERTY_KEY, PROPERTY_VALUE, DESCRIPTION) VALUES ('DEFWANMASK', '255.255.255.0', 'Subnet mask of the WAN pool a first site is numbered from');
