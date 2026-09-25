-- Makes every seeded customer orderable on a non-production stack.
--
-- Customer.isOrderingEnabled() is active AND canSubmitOrders AND hasActiveService(), and the core
-- seed deliberately parks two of its three customers on the wrong side of it: Cascade Outfitters
-- has the administration flag off and only a TERMINATED service, and Halden Logistics is inactive
-- with no service at all. That is right for the DAO tests, which need a customer on each branch,
-- and it is why this is here and not in 17_AMS_CUSTOMERS.sql or 20_AMS_SERVICES.sql.
--
-- For someone driving the portal it reads as a fault: two customers out of three answer "Ordering
-- is not enabled for this customer", and nothing on the screen says which of the three conditions
-- failed. Only the flag can be changed from Customer admin; the other two have no screen.
--
-- The rule itself is untouched. It still governs production, and switching the flag off again from
-- Customer admin still closes ordering for that customer here.

UPDATE AMS_CUSTOMERS
   SET CAN_SUBMIT_ORDERS_FL = 'Y', ACTIVE_FL = 'Y', MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = 'SEED'
 WHERE CAN_SUBMIT_ORDERS_FL <> 'Y' OR ACTIVE_FL <> 'Y';

-- An active service each for the two customers that have none. Ids sit in the demonstration range,
-- above the core seed and far below 100000 where the sequences start.
INSERT INTO AMS_SERVICES (SERVICE_ID, CUSTOMER_ID, SERVICE_TYPE_CD, SERVICE_STATUS_CD, START_DT, DESCRIPTION, CREATED_DT, CREATED_BY)
VALUES (3190, 1002, 'MGDROUTER', 'ACTIVE', SYSTIMESTAMP, 'Managed Router Service', SYSTIMESTAMP, 'SEED');
INSERT INTO AMS_SERVICES (SERVICE_ID, CUSTOMER_ID, SERVICE_TYPE_CD, SERVICE_STATUS_CD, START_DT, DESCRIPTION, CREATED_DT, CREATED_BY)
VALUES (3191, 1003, 'MGDROUTER', 'ACTIVE', SYSTIMESTAMP, 'Managed Router Service', SYSTIMESTAMP, 'SEED');
