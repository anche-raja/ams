package org.example.am.shared.schema;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Builds the schema on an empty database, from the SQL packaged alongside this class.
 *
 * <p>This replaces what {@code db/oracle/00_init.sh} did inside the Oracle container. The scripts
 * live in {@code src/main/resources/db} of this module rather than in a directory at the repository
 * root, so they reach both Surefire and the running server over the ordinary compile dependency and
 * travel inside {@code WEB-INF/lib} in the WAR. Nothing has to resolve a filesystem path, so the
 * installer behaves identically under Surefire, on a developer's Tomcat and in the container.</p>
 *
 * <p>Idempotency is not optional here. The Oracle container only ever ran its scripts on a fresh
 * volume, but an embedded database is a file that persists between restarts, so this runs against
 * an already-built schema most of the time. Two guards handle that: the DDL itself is written with
 * {@code IF NOT EXISTS} throughout, and the seed is applied only when the database is empty.</p>
 */
public class SchemaInstaller {

    private static final Logger LOGGER = LogManager.getLogger(SchemaInstaller.class);

    /**
     * Load order, and it matters: tables before the constraints and indexes that reference them,
     * and everything before the seed. Within a directory, filename order - which is why the
     * scripts carry numeric prefixes.
     */
    private static final String[] SCHEMA_LOCATIONS = {
        "classpath*:db/schema/01_tables/*.sql",
        "classpath*:db/schema/04_sequences/*.sql",
        "classpath*:db/schema/05_views/*.sql",
        "classpath*:db/schema/02_constraints/*.sql",
        "classpath*:db/schema/03_indexes/*.sql",
    };

    /**
     * The rows the tests count on, and the minimum the application needs to function. Loaded
     * everywhere.
     */
    private static final String CORE_SEED_LOCATION = "classpath*:db/seed/core/*.sql";

    /**
     * Demonstration rows - customers to click through, and the LDAP group mappings that give a
     * local developer any roles at all. Kept out of the core tier deliberately: it adds some sixty
     * rows to AMS_LDAP_GROUP_ROLES, and the tests that count those mappings would have to change.
     * Loaded only when the caller asks, which in practice means a non-production profile.
     */
    private static final String DEMO_SEED_LOCATION = "classpath*:db/seed/demo/*.sql";

    /**
     * Calendar capacity, re-applied on <em>every</em> start rather than only on an empty database.
     *
     * <p>These windows are generated relative to the current date. The Oracle original made them
     * once, at container first boot, which meant that on a database more than three weeks old the
     * despatch and installation screens were quietly empty with nothing to say why. A persistent
     * embedded file makes that failure more likely, not less, so this tier rolls forward each time.
     * The scripts clear only future slots nobody holds, so re-applying never strands a booking.</p>
     */
    private static final String ROLLING_SEED_LOCATION = "classpath*:db/seed/rolling/*.sql";

    /** Cheapest question that distinguishes "no schema" from "schema but no data". */
    private static final String COUNT_CUSTOMERS = "SELECT COUNT(*) FROM AMS_CUSTOMERS";

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * Creates the schema if it is absent and seeds it if it is empty.
     *
     * @param dataSource the database to build; never {@code null}
     */
    public void install(final DataSource dataSource) {
        install(dataSource, false);
    }

    /**
     * @param includeDemoData whether to load the demonstration tier as well; false in production,
     *                        where seeding invented customers into a real database would be a bug
     */
    public void install(final DataSource dataSource, final boolean includeDemoData) {
        try (Connection connection = dataSource.getConnection()) {
            final boolean alreadyBuilt = tableExists(connection, "AMS_CUSTOMERS");

            for (final String location : SCHEMA_LOCATIONS) {
                run(connection, location);
            }

            final boolean seeded = alreadyBuilt && countCustomers(connection) > 0;
            if (!seeded) {
                run(connection, CORE_SEED_LOCATION);
                if (includeDemoData) {
                    run(connection, DEMO_SEED_LOCATION);
                }
            }
            if (includeDemoData) {
                run(connection, ROLLING_SEED_LOCATION);
            }
            LOGGER.info("Schema ready: {} customers, {} bookable windows (demo data: {}, {})",
                    Integer.valueOf(countCustomers(connection)),
                    Integer.valueOf(countBookableWindows(connection)),
                    Boolean.valueOf(includeDemoData),
                    seeded ? "seed already present" : "seed applied");
        } catch (final SQLException | IOException failure) {
            // Deliberately fatal. An application that starts against a half-built schema fails
            // later, somewhere else, in a way nobody can trace back to here.
            // The message carries the cause's own text: a container console truncates long cause
            // chains, and this is the one line anybody debugging a failed startup will see.
            throw new IllegalStateException("Could not install the AMS schema: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage(), failure);
        }
    }

    private void run(final Connection connection, final String location) throws IOException {
        final Resource[] scripts = resolver.getResources(location);
        // getResources makes no ordering promise, and these scripts are numbered because the order
        // is load-bearing.
        final List<Resource> ordered = new ArrayList<Resource>(Arrays.asList(scripts));
        ordered.sort(Comparator.comparing(resource -> String.valueOf(resource.getFilename())));

        if (ordered.isEmpty()) {
            // Silence here would mean an empty schema and a failure much further on, so say it.
            LOGGER.warn("No scripts matched {} - the schema will be incomplete", location);
        }
        for (final Resource script : ordered) {
            ScriptUtils.executeSqlScript(connection, script);
        }
        LOGGER.info("Ran {} scripts from {}", Integer.valueOf(ordered.size()), location);
    }

    private static boolean tableExists(final Connection connection, final String tableName)
            throws SQLException {
        try (ResultSet tables = connection.getMetaData()
                .getTables(null, null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    /** What the despatch and installation screens will actually be able to offer. */
    private static int countBookableWindows(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM AMS_TIMESLOTS WHERE START_TM > CURRENT_TIMESTAMP "
                     + "AND AVAILABLE_FL = 'Y' AND RESERVED_COUNT < CAPACITY")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private static int countCustomers(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(COUNT_CUSTOMERS)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }
}
