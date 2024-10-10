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
     * all logging levels are enabled. Otherwise, all logging is disabled (including ERROR logs).
     *
     * @param properties The Properties object containing configuration.
     */
    public static void configureLogging(Properties properties) throws SQLException {
        String enableLogging = properties.getProperty("enableLogging", "false");

        boolean isEnableLogging = false; // Default to false

        if (enableLogging != null) {
            if (enableLogging.equalsIgnoreCase("true")) {
                isEnableLogging = true;
            } else if (enableLogging.equalsIgnoreCase("false")) {
                isEnableLogging = false;
            } else {
                // Handle invalid value by throwing an exception
                throw new SQLException("Invalid value for enableLogging property. Expected 'true' or 'false', but got: " + enableLogging);
            }
        }

        if (isEnableLogging) {
            // Enable all logging for the two classes
            Configurator.setLevel(CIPDriver.class.getName(), Level.ALL);
            Configurator.setLevel(CIPAvaticaHttpClient.class.getName(), Level.ALL);
            classCIPDriverLogger.debug("Logging enabled for CIPDriver.");
            classCIPAvaticaHttpClientLogger.debug("Logging enabled for CIPAvaticaHttpClient.");
        } else {
            // Disable all logging (including error) for the two classes
            Configurator.setLevel(CIPDriver.class.getName(), Level.OFF);
            Configurator.setLevel(CIPAvaticaHttpClient.class.getName(), Level.OFF);
        }
    }
}
