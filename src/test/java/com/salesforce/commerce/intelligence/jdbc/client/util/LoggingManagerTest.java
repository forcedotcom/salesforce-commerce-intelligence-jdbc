package com.salesforce.commerce.intelligence.jdbc.client.util;

import com.salesforce.commerce.intelligence.jdbc.client.CIPAvaticaHttpClient;
import com.salesforce.commerce.intelligence.jdbc.client.CIPDriver;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class LoggingManagerTest {

    @Before
    public void setUp() {
        // Reset logging levels to a known state before each test
        Configurator.setLevel(CIPDriver.class.getName(), Level.OFF);
        Configurator.setLevel(CIPAvaticaHttpClient.class.getName(), Level.OFF);
    }

    @After
    public void tearDown() {
        // Reset logging levels to a known state after each test
        Configurator.setLevel(CIPDriver.class.getName(), Level.OFF);
        Configurator.setLevel(CIPAvaticaHttpClient.class.getName(), Level.OFF);
    }

    @Test
    public void testConfigureLogging_EnableTrue() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("enableLogging", "true");

        LoggingManager.configureLogging(properties);

        // Verify logging levels
        assertEquals("CIPDriver logging level should be ALL", Level.ALL, LogManager.getLogger(CIPDriver.class).getLevel());
        assertEquals("CIPAvaticaHttpClient logging level should be ALL", Level.ALL, LogManager.getLogger(CIPAvaticaHttpClient.class)
                .getLevel());
    }

    @Test
    public void testConfigureLogging_EnableFalse() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("enableLogging", "false");

        LoggingManager.configureLogging(properties);

        // Verify logging levels
        assertEquals("CIPDriver logging level should be ERROR", Level.ERROR, LogManager.getLogger(CIPDriver.class).getLevel());
        assertEquals("CIPAvaticaHttpClient logging level should be ERROR", Level.ERROR, LogManager.getLogger(CIPAvaticaHttpClient.class)
                .getLevel());
    }

    @Test
    public void testConfigureLogging_DefaultToFalse() throws SQLException {
        Properties properties = new Properties();

        LoggingManager.configureLogging(properties);

        // Verify logging levels default to ERROR
        assertEquals("CIPDriver logging level should default to ERROR", Level.ERROR, LogManager.getLogger(CIPDriver.class).getLevel());
        assertEquals("CIPAvaticaHttpClient logging level should default to ERROR", Level.ERROR,
                LogManager.getLogger(CIPAvaticaHttpClient.class).getLevel());
    }

    @Test(expected = SQLException.class)
    public void testConfigureLogging_InvalidValue() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("enableLogging", "invalid");

        // This should throw an SQLException
        LoggingManager.configureLogging(properties);
    }
}
