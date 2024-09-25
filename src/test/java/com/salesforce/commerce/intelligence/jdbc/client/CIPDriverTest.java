package com.salesforce.commerce.intelligence.jdbc.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit Test
 */

public class CIPDriverTest {

    private CIPDriver cipDriver;
    private Properties properties;
    private Connection mockConnection;
    CIPDriver spyDriver;

    private String validURL = "jdbc:salesforcecc:testUrl";
    private String invalidURL = "jdbc:invalid:testUrl";

    @Before
    public void setUp() {
        properties = new Properties();
        properties.setProperty("user", "testUser");
        properties.setProperty("password", "testPass");
        properties.setProperty("amOauthHost", "testHost");

        cipDriver = new CIPDriver();
        mockConnection = mock(Connection.class);
        // Create a spy of CIPDriver
        spyDriver = Mockito.spy(cipDriver);
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        assertTrue("Driver should accept URL starting with jdbc:salesforce:remote", cipDriver.acceptsURL(validURL));
        assertFalse("Driver should not accept URL not starting with jdbc:salesforce:remote", cipDriver.acceptsURL(invalidURL));
    }

    @Test
    public void testGetConnectStringPrefix() {
        assertEquals("Connection string prefix should match", "jdbc:salesforcecc:", cipDriver.getConnectStringPrefix());
    }

    @Test(expected = SQLException.class)
    public void testConnectWithInvalidUrl() throws SQLException {
        cipDriver.connect(invalidURL, properties);
    }

    @Test
    public void testConnect_withSSLPropertyTrue() throws SQLException {
        // Setup
        properties.setProperty("ssl", "true");

        // Create a mock map
        Map<String, String> mockMap = mock(Map.class);

        // Define behavior for the mock map
        when(mockMap.get("access_token")).thenReturn("mockToken");
        when(mockMap.get("expires_in")).thenReturn("3600");

        String url = "jdbc:salesforcecc://localhost:5432/mydatabase";

        // Mocking the super.connect method
        when(spyDriver.doConnect(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(mockConnection);

        // Execute
        Connection connection = spyDriver.connect(url, properties);

        // Validate
        assertNotNull("Connection should not be null when URL is correct", connection);
        CIPDriver.ConnectionResult result = cipDriver.convertPostgresUrlToAvatica(url, true);
        assertEquals("jdbc:salesforcecc:url=https://localhost:5432", result.getModifiedUrl());
    }

    @Test
    public void testConnect_withSSLPropertyFalse() throws SQLException {
        // Setup
        properties.setProperty("ssl", "false");

        // Create a mock map
        Map<String, String> mockMap = mock(Map.class);

        // Define behavior for the mock map
        when(mockMap.get("access_token")).thenReturn("mockToken");
        when(mockMap.get("expires_in")).thenReturn("3600");

        String url = "jdbc:salesforcecc://localhost:5432/mydatabase";

        // Mocking the super.connect method
        when(spyDriver.doConnect(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(mockConnection);

        // Execute
        Connection connection = spyDriver.connect(url, properties);

        // Validate
        assertNotNull("Connection should not be null when URL is correct", connection);
        CIPDriver.ConnectionResult result = cipDriver.convertPostgresUrlToAvatica(url, false);
        assertEquals("jdbc:salesforcecc:url=http://localhost:5432", result.getModifiedUrl());
    }

    @Test
    public void testConnect_withNoSSLProperty() throws SQLException {
        String url = "jdbc:salesforcecc://localhost:5432/mydatabase";

        // Create a mock map
        Map<String, String> mockMap = mock(Map.class);

        // Define behavior for the mock map
        when(mockMap.get("access_token")).thenReturn("mockToken");
        when(mockMap.get("expires_in")).thenReturn("3600");

        // Mocking the super.connect method
        when(spyDriver.doConnect(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(mockConnection);

        // Execute
        Connection connection = spyDriver.connect(url, properties);

        // Validate
        CIPDriver.ConnectionResult result = cipDriver.convertPostgresUrlToAvatica(url, true);
        assertEquals("jdbc:salesforcecc:url=https://localhost:5432", result.getModifiedUrl());
    }

    @Test
    public void testConnect_withInvalidSSLProperty()
                    throws SQLException
    {
        // Setup
        properties.setProperty("ssl", "invalid");

        String url = "jdbc:salesforcecc://localhost:5432/mydatabase";

        // Create a mock map
        Map<String, String> mockMap = mock(Map.class);

        // Define behavior for the mock map
        when(mockMap.get("access_token")).thenReturn("mockToken");
        when(mockMap.get("expires_in")).thenReturn("3600");

        // Mocking the super.connect method
        when(spyDriver.doConnect(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(mockConnection);

        // Execute & Validate
        SQLException thrown = assertThrows(SQLException.class, () -> {
            spyDriver.connect(url, properties);
        });

        assertEquals("Invalid value for ssl property. Expected 'true' or 'false', but got: invalid", thrown.getMessage());
    }
}
