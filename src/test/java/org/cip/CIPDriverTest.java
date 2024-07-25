package org.cip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class CIPDriverTest {

    private CIPDriver CIPDriver;
    private Properties properties;
    private String validURL = "jdbc:salesforce:remote:testUrl";
    private String invalidURL = "jdbc:invalid:remote";

    @Before
    public void setUp() {
        CIPDriver = new CIPDriver();
        properties = new Properties();
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        assertTrue("Driver should accept URL starting with jdbc:salesforce:remote", CIPDriver.acceptsURL(validURL));
        assertFalse("Driver should not accept URL not starting with jdbc:salesforce:remote", CIPDriver.acceptsURL(invalidURL));
    }

    @Test
    public void testGetConnectStringPrefix() {
        assertEquals("Connection string prefix should match", "jdbc:salesforce:remote:", CIPDriver.getConnectStringPrefix());
    }

    @Ignore
    @Test
    public void testConnect() throws SQLException {
        // Set up to simulate a connection
        properties.setProperty("fun", "postgresql");

        // Mocking DriverManager to return a mock connection
        Connection mockConnection = Mockito.mock(Connection.class);
        DriverManager.registerDriver(CIPDriver);
        Mockito.when(DriverManager.getConnection(validURL, properties)).thenReturn(mockConnection);

        Mockito.when(DriverManager.getConnection(validURL, properties)).thenReturn(mockConnection);

        // Perform the connection
        Connection connection = CIPDriver.connect(validURL, properties);

        assertNotNull("Connection should not be null when URL is correct", connection);
        assertEquals("Property 'fun' should be set to 'postgresql'", "postgresql", properties.getProperty("fun"));
    }

    @Test(expected = SQLException.class)
    public void testConnectWithInvalidUrl() throws SQLException {
        CIPDriver.connect(invalidURL, properties);
    }
}
