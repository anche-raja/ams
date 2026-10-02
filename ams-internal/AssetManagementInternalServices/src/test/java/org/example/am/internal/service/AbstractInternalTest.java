package org.example.am.internal.service;

import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for the internal module's integration tests.
 *
 * <p>The context names are prefixed so they cannot be confused with the shared module's, which are
 * on the classpath too by way of its test-jar.</p>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:internal-test-context-h2.xml",
        "classpath:internal-test-context-scan.xml" })
@Transactional
public abstract class AbstractInternalTest {

    protected static final String TEST_USER = "junit";
    protected static final String OPS_USER = "opsuser";

    protected static final long CUSTOMER_ID = 1001L;
    protected static final long OTHER_CUSTOMER_ID = 1002L;

    protected static final long HEALTHY_ASSET_ID = 5001L;
    protected static final long ATTENTION_ASSET_ID = 5002L;
}
