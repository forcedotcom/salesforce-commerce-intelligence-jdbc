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

    @Test
    public void testCaseSensitive_shouldFailDueToCaseMismatch()
    {
        Properties properties = new Properties();
        properties.put( "ssl",
                        "true" ); // for testing against local cip-service-dataconnector set ssl value to false
        properties.put( "user", "fff01280-e3c3-43e5-8006-5ea1301f9c50" );
        properties.put( "password", "Demandware1!" );
        properties.put( "amOauthHost", "https://account-pod5.demandware.net" );
        // properties.put( "enableLogging", "true" );

        SQLException exception = assertThrows( SQLException.class, () -> {
            Class.forName( "com.salesforce.commerce.intelligence.jdbc.client.CIPDriver" );

            try (Connection conn = DriverManager.getConnection(
                            "jdbc:salesforcecc://jdbc.qa.analytics-dev.commercecloud.salesforce.com:443/bjmp_prd",
                            properties ); Statement statement = conn.createStatement())
            {

                // This should fail if "DAY_ID" does not match the actual case in the schema
                statement.executeQuery( "SELECT DAY_ID FROM ccdw_dim_date LIMIT 5" );
            }
        } );

        assertTrue( "Expected error message to mention DAY_ID or column case issue",
                        exception.getMessage().toLowerCase().contains( "day_id" ) || exception.getMessage()
                                        .toLowerCase().contains( "column" ) );
    }

    @Test
    public void testCaseSensitive() throws Exception {
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
            ResultSet resultSet = statement.executeQuery("SELECT day_id FROM ccdw_dim_date LIMIT 5");

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
        assertTrue("Expected error message to contain 400 Bad Request and invalid scope error", 
            sqlException.getMessage().contains("400 Bad Request") && 
            sqlException.getMessage().contains("Unknown/invalid tenant scope: SALESFORCE_COMMERCE_API:bjxl_prd"));
    }

    /**
     * Test to verify error handling for HTTP responses with response body.
     */
    @Test
    public void testCIPDriver_ErrorResponseHandling() {
        Properties properties = new Properties();
        properties.put("ssl", "true");
        properties.put("user", "fff01280-e3c3-43e5-8006-5ea1301f9c50");
        properties.put("password", "Demandware1!");
        properties.put("amOauthHost", "https://account-pod5.demandware.net");

        // Test case 1: Error with response body
        Exception exceptionWithBody = assertThrows(SQLException.class, () -> {
            Class.forName("com.salesforce.commerce.intelligence.jdbc.client.CIPDriver");
            // Use an invalid query that will return a 400 with response body
            Connection conn = DriverManager.getConnection(
                "jdbc:salesforcecc://jdbc.qa.analytics-dev.commercecloud.salesforce.com:443/bjmp_prd", 
                properties);
            Statement statement = conn.createStatement();
            statement.executeQuery("INVALID QUERY SYNTAX");
        });

        // Verify the error message contains both status code and response body
        assertTrue("Error message should contain status code", 
            exceptionWithBody.getMessage().contains("HTTP request failed with status code 400: Bad Request"));
        assertTrue("Error message should contain response body", 
            exceptionWithBody.getMessage().contains("Caused by: java.sql.SQLException: Error while executing SQL \"INVALID QUERY SYNTAX\""));
 
    }
}
