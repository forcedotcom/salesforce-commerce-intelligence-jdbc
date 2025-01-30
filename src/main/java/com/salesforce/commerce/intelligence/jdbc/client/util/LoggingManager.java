package com.salesforce.commerce.intelligence.jdbc.client.util;

import com.salesforce.commerce.intelligence.jdbc.client.CIPAvaticaHttpClient;
import com.salesforce.commerce.intelligence.jdbc.client.CIPDriver;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.Properties;

public class LoggingManager {

    // Loggers for two example classes
    private static final Logger classCIPDriverLogger = LogManager.getLogger(CIPDriver.class);
    private static final Logger classCIPAvaticaHttpClientLogger = LogManager.getLogger(CIPAvaticaHttpClient.class);

    /**
     * Configures logging for two classes based on the 'enableLogging' property from a Properties object. If enableLogging is set to 'true',
     * all logging levels are enabled. Otherwise, only ERROR logging is enabled.
     *
     * @param properties The Properties object containing configuration.
     */
    public static void configureLogging(Properties properties) throws SQLException {
        String enableLogging = properties.getProperty("enableLogging", "false").toLowerCase();

        switch (enableLogging) {
        case "true":
            // Enable all logging for the two classes
            Configurator.setLevel(CIPDriver.class.getName(), Level.ALL);
            Configurator.setLevel(CIPAvaticaHttpClient.class.getName(), Level.ALL);
            classCIPDriverLogger.debug("Logging enabled for CIPDriver.");
            classCIPAvaticaHttpClientLogger.debug("Logging enabled for CIPAvaticaHttpClient.");
            break;

        case "false":
            // Enable only ERROR logging for the two classes
            Configurator.setLevel(CIPDriver.class.getName(), Level.ERROR);
            Configurator.setLevel(CIPAvaticaHttpClient.class.getName(), Level.ERROR);
            break;

        default:
            // Handle invalid value by throwing an exception
            throw new SQLException("Invalid value for enableLogging property. Expected 'true' or 'false', but got: " + enableLogging);
        }
    }
}
