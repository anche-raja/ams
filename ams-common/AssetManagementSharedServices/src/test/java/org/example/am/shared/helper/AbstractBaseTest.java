package org.example.am.shared.helper;

import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for the integration tests.
 *
 * <p>Brings up the embedded schema and the component-scanned beans once per JVM, and wraps every
 * test method in a transaction that is rolled back afterwards, so tests that insert rows do not
 * have to clean up after themselves and cannot see each other's writes.</p>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:test-context-h2.xml",
        "classpath:test-context-scan.xml" })
@Transactional
public abstract class AbstractBaseTest {

    /** Identifier written into the audit columns by tests that insert. */
    protected static final String TEST_USER = "junit";

    /** Seeded customer that owns most of the fixture data. */
    protected static final long CUSTOMER_ID = 1001L;

    /** A second customer, used to prove the customer-scoped lookups do not leak. */
    protected static final long OTHER_CUSTOMER_ID = 1002L;

    /** Seeded asset with nothing in flight against it. */
    protected static final long HEALTHY_ASSET_ID = 5001L;

    /** Seeded asset blocked by an open order. */
    protected static final long BLOCKED_BY_ORDER_ASSET_ID = 5002L;

    /** Seeded asset whose stored configuration does not match the device. */
    protected static final long MISMATCHED_ASSET_ID = 5003L;
}
