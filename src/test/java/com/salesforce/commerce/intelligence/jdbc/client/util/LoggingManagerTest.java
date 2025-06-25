package com.salesforce.commerce.intelligence.jdbc.client.util;

import com.salesforce.commerce.intelligence.jdbc.client.CIPAvaticaHttpClient;
import com.salesforce.commerce.intelligence.jdbc.client.CIPDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class LoggingManagerTest {

    @Before
    public void setUp() {
        // No-op for SLF4J migration
    }

    @After
    public void tearDown() {
        // No-op for SLF4J migration
    }

    @Test
    public void testConfigureLogging_EnableTrue() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("enableLogging", "true");
        LoggingManager.configureLogging(properties);
        // No assertion on log level; just ensure no exception is thrown
    }

    @Test
    public void testConfigureLogging_EnableFalse() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("enableLogging", "false");
        LoggingManager.configureLogging(properties);
        // No assertion on log level; just ensure no exception is thrown
    }

    @Test
    public void testConfigureLogging_DefaultToFalse() throws SQLException {
        Properties properties = new Properties();
        LoggingManager.configureLogging(properties);
        // No assertion on log level; just ensure no exception is thrown
    }

    @Test(expected = SQLException.class)
    public void testConfigureLogging_InvalidValue() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("enableLogging", "invalid");

        // This should throw an SQLException
        LoggingManager.configureLogging(properties);
    }
}
