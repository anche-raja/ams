package org.example.am.shared.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The acceptance gate for the schema, moved into the build.
 *
 * <p>It used to live in {@code db/oracle/09_validate/validate.sql} and run inside the Oracle
 * container, which meant it ran at most once per volume and never in CI. The counts it asserted
 * had already drifted from the DDL by the time anyone looked.</p>
 */
public class SchemaInstallerTest {

    /** A distinct in-memory database per test, so ordering between tests cannot matter. */
    private JdbcDataSource dataSource;

    @BeforeEach
    public void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:schema-" + System.nanoTime()
                + ";MVCC=TRUE;LOCK_TIMEOUT=5000;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
    }

    @Test
    public void theSchemaAndCoreSeedInstallOnAnEmptyDatabase() throws Exception {
        new SchemaInstaller().install(dataSource);

        assertEquals("tables", 35, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'TABLE'"));
        assertEquals("views", 1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS "
                + "WHERE TABLE_SCHEMA = 'PUBLIC'"));
        assertEquals("sequences", 19, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SEQUENCES "
                + "WHERE SEQUENCE_SCHEMA = 'PUBLIC'"));
        assertEquals("foreign keys", 32, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = 'PUBLIC' AND CONSTRAINT_TYPE = 'REFERENTIAL'"));
        assertEquals("check constraints", 21, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = 'PUBLIC' AND CONSTRAINT_TYPE = 'CHECK'"));
    }

    /**
     * The two tables that exist only for the scheduling logic. They were absent from the old H2
     * fixtures, which is why nothing could ever run a reservation under test.
     */
    @Test
    public void theLedgerTablesTheSchedulingLogicNeedsArePresent() throws Exception {
        new SchemaInstaller().install(dataSource);

        assertEquals(0, count("SELECT COUNT(*) FROM AMS_TIMESLOT_RESERVATIONS"));
        assertEquals(0, count("SELECT COUNT(*) FROM AMS_CIRCUIT_WINDOWS"));
    }

    /**
     * The capacity guard is the backstop for the counter arithmetic. If this stops enforcing, a
     * counter bug silently double-books an engineer instead of failing.
     */
    @Test
    public void theTimeslotCapacityCheckIsEnforced() throws Exception {
        new SchemaInstaller().install(dataSource);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO AMS_TIMESLOTS "
                    + "(TIMESLOT_ID, CALL_TYPE_CD, CAPACITY, RESERVED_COUNT, AVAILABLE_FL) "
                    + "VALUES (990001, 'SHIP', 1, 0, 'Y')");
            try {
                statement.executeUpdate(
                        "UPDATE AMS_TIMESLOTS SET RESERVED_COUNT = 2 WHERE TIMESLOT_ID = 990001");
                throw new AssertionError("an over-capacity write was accepted");
            } catch (final java.sql.SQLException expected) {
                assertEquals("H2 check-constraint violation", 23513, expected.getErrorCode());
            }
        }
    }

    /** File-mode H2 persists, so install runs against a built schema far more often than not. */
    @Test
    public void installingTwiceIsHarmless() throws Exception {
        final SchemaInstaller installer = new SchemaInstaller();
        installer.install(dataSource);
        final int customersAfterFirst = count("SELECT COUNT(*) FROM AMS_CUSTOMERS");

        installer.install(dataSource);

        assertEquals("the seed was applied twice", customersAfterFirst,
                count("SELECT COUNT(*) FROM AMS_CUSTOMERS"));
    }

    @Test
    public void theDemoTierIsOptOutAndAddsTheRoleMappings() throws Exception {
        new SchemaInstaller().install(dataSource, true);

        // The core tier alone carries only a handful of mappings; the demo tier is what gives a
        // local developer enough roles to reach any screen.
        assertTrue("demo role mappings were not loaded",
                count("SELECT COUNT(*) FROM AMS_LDAP_GROUP_ROLES") > 20);
    }

    @Test
    public void theDemoTierLeavesEveryCustomerAbleToOrder() throws Exception {
        new SchemaInstaller().install(dataSource, true);

        // The three conditions of Customer.isOrderingEnabled(), asked of the rows directly. The
        // core tier parks customers on the failing side of each one for the DAO tests; on a stack
        // somebody is clicking through, that reads as two customers out of three being broken.
        assertEquals("a customer is inactive or has ordering switched off", 0,
                count("SELECT COUNT(*) FROM AMS_CUSTOMERS "
                        + "WHERE ACTIVE_FL <> 'Y' OR CAN_SUBMIT_ORDERS_FL <> 'Y'"));
        assertEquals("a customer has no active service", 0,
                count("SELECT COUNT(*) FROM AMS_CUSTOMERS c WHERE NOT EXISTS ("
                        + "SELECT 1 FROM AMS_SERVICES s WHERE s.CUSTOMER_ID = c.CUSTOMER_ID "
                        + "AND s.SERVICE_STATUS_CD = 'ACTIVE')"));
    }

    private int count(final String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }
}
