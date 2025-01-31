package com.salesforce.commerce.intelligence.jdbc.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Properties;

import org.junit.Ignore;
import org.junit.Test;

/**
 * This test verifies if the CIPDriver is loading correctly and can communicate with the Account Manager to obtain an OAuth token. Note:
 * This test is requires that cip-service-dataconnector is running in QA env with instance bjmp_prd as dependencies otherwise tests won't
 * work in Jenkins CI environments. IMPORTANT: // for testing against local cip-service-dataconnector set ssl value to false // for testing
 * against local cip-service-dataconnector set url as "jdbc:salesforcecc://localhost:9787/bjnl_prd"
 */

// @Ignore
public class CIPDriverIT {

    @Test
    public void testCIPDriver() throws Exception {
        try {
            Properties properties = new Properties();
            properties.put("ssl", "true"); // for testing against local cip-service-dataconnector set ssl value to false
            properties.put("user", "fff01280-e3c3-43e5-8006-5ea1301f9c50");
            properties.put("password", "Demandware1!");
            properties.put("amOauthHost", "https://account-pod5.demandware.net");
            // properties.put( "enableLogging", "true" );

            Class.forName("com.salesforce.commerce.intelligence.jdbc.client.CIPDriver");

            // for local testing set url as "jdbc:salesforcecc://localhost:9787/bjnl_prd"
            Connection conn = DriverManager.getConnection(
                    "jdbc:salesforcecc://jdbc.qa.analytics-dev.commercecloud.salesforce.com:443/bjmp_prd", properties);

            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * from ccdw_dim_date LIMIT 5");

            // Iterate through the results (if any) to ensure the query is processed
            while (resultSet.next()) {
                System.out.println(resultSet.getString(1));
            }

            System.out.println("Connection established: " + (conn != null));
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Test to verify that a 401 Unauthorized error is thrown when using incorrect client ID.
     */
    @Test
    public void testCIPDriver_401_test() {
        Properties properties = new Properties();
        properties.put("ssl", "true");
        // Incorrect client ID, expecting 401 error
        properties.put("user", "fff01280-e3c3-43e5-8006-5ea1301f9c5");
        properties.put("password", "Demandware1!");
        properties.put( "amOauthHost", "https://account-pod5.demandware.net" );

        Exception exception = assertThrows(RuntimeException.class, () -> {
            // Load custom driver
            Class.forName( "com.salesforce.commerce.intelligence.jdbc.client.CIPDriver" );

            // Attempt to establish a connection with incorrect credentials
            Connection conn = DriverManager.getConnection("jdbc:salesforcecc://jdbc.qa.analytics-dev.commercecloud.salesforce.com:443/bjmp_prd", properties);
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT 1");

            // Iterate through the results (if any) to ensure the query is processed
            while (resultSet.next()) {
                System.out.println(resultSet.getString(1));
            }
        });

        // Check that the root cause of the exception is a SQLException (wrapped in RuntimeException)
        assertTrue( "Expected cause to be SQLException", exception.getCause() instanceof SQLException);
        SQLException sqlException = (SQLException) exception.getCause();

        // Optionally, assert more details about the SQLException (like the SQLState, error code, or message)
        assertEquals("401 Unauthorized. Please verify your username and password.", sqlException.getMessage());
    }

    /**
     * Test to verify that a 400 bad error is thrown when using incorrect tenant ID.
     */
    @Test
    public void testCIPDriver_400_test() {
        Properties properties = new Properties();
        properties.put("ssl", "true");
        properties.put("user", "fff01280-e3c3-43e5-8006-5ea1301f9c50");
        properties.put("password", "Demandware1!");

        // Test against non-prod env
        properties.put( "amOauthHost", "https://account-pod5.demandware.net" );

        Exception exception = assertThrows(RuntimeException.class, () -> {
            // Load custom driver
            Class.forName( "com.salesforce.commerce.intelligence.jdbc.client.CIPDriver" );

            // Attempt to establish a connection with valid credentials but restricted access (403)
            // Use valid client ID but simulate a 400 error (insufficient access rights)
            Connection conn = DriverManager.getConnection("jdbc:salesforcecc://jdbc.qa.analytics-dev.commercecloud.salesforce.com:443/bjxl_prd", properties);
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT 1");

            // Iterate through the results (if any) to ensure the query is processed
            while (resultSet.next()) {
                System.out.println(resultSet.getString(1));
            }
        });

        // Check that the root cause of the exception is a SQLException (wrapped in RuntimeException)
        assertTrue("Expected cause to be SQLException", exception.getCause() instanceof SQLException);
        SQLException sqlException = (SQLException) exception.getCause();

        // Optionally, assert more details about the SQLException (like the SQLState, error code, or message)
        assertEquals("400 Bad Request.{\"error_description\":\"Unknown/invalid tenant scope: SALESFORCE_COMMERCE_API:bjxl_prd\",\"error\":\"invalid_scope\"}", sqlException.getMessage());
    }
}
