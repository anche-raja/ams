package org.example.am.internal.web.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.example.am.shared.schema.SchemaInstaller;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * The data source and the transaction manager.
 *
 * <p>The pool is the container's, looked up over JNDI, so connection sizing, validation and
 * failover are configured in Tomcat's {@code META-INF/context.xml} (or a per-instance descriptor
 * under {@code conf/Catalina/localhost}) rather than in the application. There is no ORM, so the
 * transaction manager is the plain JDBC one: a {@code @Transactional} service method and the
 * {@code NamedParameterJdbcTemplate} calls underneath it share one connection and one
 * transaction.</p>
 */
@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

    /** Matches the {@code name} on the Tomcat {@code <Resource>} and the resource-ref in web.xml. */
    public static final String DATA_SOURCE_JNDI_NAME = "jdbc/amsInternalDS";

    @Bean
    public DataSource dataSource(final Environment environment) {
        final JndiDataSourceLookup lookup = new JndiDataSourceLookup();
        // Prefixes the name with java:comp/env, which is how the resource-ref in web.xml exposes it.
        lookup.setResourceRef(true);
        final DataSource dataSource = lookup.getDataSource(DATA_SOURCE_JNDI_NAME);

        // Built here, inside the factory method, rather than by a separate initializer bean: this
        // returns the DataSource only once the schema exists, so every bean that injects it is
        // ordered behind the install with no @DependsOn graph to keep in step. On a server whose
        // schema is already present this is two metadata queries and a count.
        // Demonstration rows only outside production. Seeding invented customers into a real
        // database would be a bug, so the decision is taken from the same profile that chooses the
        // developer authentication stub over the WebSEAL filter.
        final boolean nonProduction =
                !environment.acceptsProfiles(Profiles.of(CommonConstants.PROFILE_PRODUCTION,
                                                         CommonConstants.PROFILE_QA));
        new SchemaInstaller().install(dataSource, nonProduction);
        return dataSource;
    }

    @Bean
    public PlatformTransactionManager transactionManager(final DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
