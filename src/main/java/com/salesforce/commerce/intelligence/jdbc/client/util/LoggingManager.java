package com.salesforce.commerce.intelligence.jdbc.client.util;

import com.salesforce.commerce.intelligence.jdbc.client.CIPAvaticaHttpClient;
import com.salesforce.commerce.intelligence.jdbc.client.CIPDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Properties;

public class LoggingManager {

    // Note: With SLF4J + Logback, the 'enableLogging' property does not change log levels.
    // Logging is controlled by logback.xml. This property is kept for backward compatibility.

    // Loggers for two example classes
    private static final Logger classCIPDriverLogger = LoggerFactory.getLogger(CIPDriver.class);
    private static final Logger classCIPAvaticaHttpClientLogger = LoggerFactory.getLogger(CIPAvaticaHttpClient.class);

    /**
     * Configures logging for two classes based on the 'enableLogging' property from a Properties object.
     *
     * <p>
     * Note: With SLF4J + Logback, the 'enableLogging' property does not change log levels. Logging is controlled by logback.xml. This
     * property is kept for backward compatibility.
     * </p>
     *
     * @param properties The Properties object containing configuration.
     */
    public static void configureLogging(Properties properties) throws SQLException {
        String enableLogging = properties.getProperty("enableLogging", "false").toLowerCase();

        switch (enableLogging) {
        case "true":
            classCIPDriverLogger.debug("Logging enabled for CIPDriver.");
            classCIPAvaticaHttpClientLogger.debug("Logging enabled for CIPAvaticaHttpClient.");
            break;

        case "false":
            // Only ERROR logs will be output if the backend is configured accordingly.
            break;

        default:
            throw new SQLException("Invalid value for enableLogging property. Expected 'true' or 'false', but got: " + enableLogging);
        }
    }
}
