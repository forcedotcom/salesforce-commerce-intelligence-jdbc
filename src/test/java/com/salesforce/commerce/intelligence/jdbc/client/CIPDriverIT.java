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
 * This test is disabled by default as it requires external dependencies and won't work in Jenkins CI environments. However, it is useful
 * for local testing with authentication. To run this test locally, simply enable it by removing the appropriate annotations or comments.
 */
@Ignore
public class CIPDriverIT {

    @Test
    public void testCIPDriver()
    {
        try
        {
            Enumeration<java.sql.Driver> driverList = DriverManager.getDrivers();
            while ( driverList.hasMoreElements() )
            {
                java.sql.Driver driver = driverList.nextElement();
                System.out.println( "Driver: " + driver.getClass().getName() );
            }

            Properties properties = new Properties();
            properties.put( "ssl", "false" );
            properties.put( "user", "fff01280-e3c3-43e5-8006-5ea1301f9c50" );
            properties.put( "password", "Demandware1!" );
            properties.put( "amOauthHost", "https://account-pod5.demandware.net" );

            Class.forName( "com.salesforce.commerce.intelligence.jdbc.client.CIPDriver" );
            Connection conn = DriverManager.getConnection( "jdbc:salesforcecc://localhost:9787/bjnl_prd",
                             properties );
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT 1");

            // Iterate through the results (if any) to ensure the query is processed
            while (resultSet.next()) {
                System.out.println(resultSet.getInt(1));
            }

            System.out.println( "Connection established: " + ( conn != null ) );
        }
        catch ( ClassNotFoundException | SQLException e )
        {
            e.printStackTrace();
        }
    }

    /**
     * Test to verify that a 401 Unauthorized error is thrown when using incorrect client ID.
     */
    @Test
    public void testCIPDriver_401_test() {
        Properties properties = new Properties();
        properties.put("ssl", "false");
        // Incorrect client ID, expecting 401 error
        properties.put("user", "fff01280-e3c3-43e5-8006-5ea1301f9c5");
        properties.put("password", "Demandware1!");
        properties.put( "amOauthHost", "https://account-pod5.demandware.net" );

        Exception exception = assertThrows(RuntimeException.class, () -> {
            // Load custom driver
            Class.forName( "com.salesforce.commerce.intelligence.jdbc.client.CIPDriver" );

            // Attempt to establish a connection with incorrect credentials
            Connection conn = DriverManager.getConnection("jdbc:salesforcecc://localhost:9787/bjnl_prd", properties);
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT 1");

            // Iterate through the results (if any) to ensure the query is processed
            while (resultSet.next()) {
                System.out.println(resultSet.getInt(1));
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
        properties.put("ssl", "false");
        properties.put("user", "fff01280-e3c3-43e5-8006-5ea1301f9c50");
        properties.put("password", "Demandware1!");

        // Test against non-prod env
        properties.put( "amOauthHost", "https://account-pod5.demandware.net" );

        Exception exception = assertThrows(RuntimeException.class, () -> {
            // Load custom driver
            Class.forName( "com.salesforce.commerce.intelligence.jdbc.client.CIPDriver" );

            // Attempt to establish a connection with valid credentials but restricted access (403)
            // Use valid client ID but simulate a 400 error (insufficient access rights)
            Connection conn = DriverManager.getConnection("jdbc:salesforcecc://localhost:9787/bjxl_prd", properties);
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT 1");

            // Iterate through the results (if any) to ensure the query is processed
            while (resultSet.next()) {
                System.out.println(resultSet.getInt(1));
            }
        });

        // Check that the root cause of the exception is a SQLException (wrapped in RuntimeException)
        assertTrue("Expected cause to be SQLException", exception.getCause() instanceof SQLException);
        SQLException sqlException = (SQLException) exception.getCause();

        // Optionally, assert more details about the SQLException (like the SQLState, error code, or message)
        assertEquals("400 Bad Request.{\"error_description\":\"Unknown/invalid tenant scope: SALESFORCE_COMMERCE_API:bjxl_prd\",\"error\":\"invalid_scope\"}", sqlException.getMessage());
    }
}
