package org.example.am.internal.web.listener;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.logging.LoggingConstants;

/**
 * Writes the built version to a dedicated logger on start-up.
 *
 * <p>The version logger has its own appender, so that "which build is actually running" can be
 * answered from the log file without grepping through application noise - the first question asked
 * on nearly every production call.</p>
 */
public class ApplicationVersionListener implements ServletContextListener {

    private static final Logger LOGGER = LogManager.getLogger(ApplicationVersionListener.class);
    private static final Logger VERSION_LOGGER =
            LogManager.getLogger(LoggingConstants.APP_VERSION_LOGGER);

    private static final String VERSION_PROPERTIES = "/appVersion.properties";

    /** Also stashed in the servlet context, so the page footer can render it. */
    public static final String CONTEXT_ATTRIBUTE_VERSION = "amsApplicationVersion";

    @Override
    public void contextInitialized(final ServletContextEvent event) {
        final Properties properties = new Properties();
        InputStream stream = null;
        try {
            stream = getClass().getResourceAsStream(VERSION_PROPERTIES);
            if (stream == null) {
                LOGGER.warn("{} is not on the classpath; version will be reported as unknown",
                        VERSION_PROPERTIES);
            } else {
                properties.load(stream);
            }
        } catch (final IOException unreadable) {
            LOGGER.warn("Could not read {}", VERSION_PROPERTIES, unreadable);
        } finally {
            closeQuietly(stream);
        }

        final String version = properties.getProperty("application.version", "unknown");
        final String buildNumber = properties.getProperty("application.build", "unknown");
        final String descriptor = version + " (build " + buildNumber + ")";

        event.getServletContext().setAttribute(CONTEXT_ATTRIBUTE_VERSION, descriptor);
        VERSION_LOGGER.info("AMS Internal Asset Management starting: {}", descriptor);
    }

    @Override
    public void contextDestroyed(final ServletContextEvent event) {
        VERSION_LOGGER.info("AMS Internal Asset Management stopping");
    }

    private static void closeQuietly(final InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (final IOException ignored) {
            LOGGER.trace("Failed to close the version properties stream", ignored);
        }
    }
}
