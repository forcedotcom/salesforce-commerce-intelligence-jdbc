package com.salesforce.commerce.intelligence.jdbc.client;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;

public class CIPAvaticaHttpClientTest {

    private CIPAvaticaHttpClient client;
    private AmAuthService mockAuthService;
    private URL mockUrl;
    private HttpURLConnection mockConnection;

    private static final String ERROR_MESSAGE = "Unauthorized access";

    @Before
    public void setUp() throws Exception {
        // Mock the URL
        mockUrl = mock(URL.class);

        // Mock the HttpURLConnection
        mockConnection = mock(HttpURLConnection.class);
        when(mockUrl.openConnection()).thenReturn(mockConnection);

        // Mock the OutputStream to avoid NullPointerException
        OutputStream mockOutputStream = new ByteArrayOutputStream();
        when(mockConnection.getOutputStream()).thenReturn(mockOutputStream);

        // Mock the AmAuthService
        mockAuthService = mock(AmAuthService.class);

        // Set mock properties
        Properties properties = new Properties();
        properties.put("amOauthHost", "mock-host");
        properties.put("user", "mock-user");
        properties.put("password", "mock-password");
        properties.put("instanceId", "mock-instance");

        // Mock the connection properties being used by the CIPDriver class
        CIPDriver.connectionProperties = new ThreadLocal<>();
        CIPDriver.connectionProperties.set(properties);

        // Initialize the client with the mocked AmAuthService
        client = new CIPAvaticaHttpClient(mockUrl, mockAuthService);
    }

    @Test
    public void testSend_GenerateTokenFirstTime() throws Exception {
        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "mock-token");
        tokenResponse.put("expires_in", "3600");

        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(tokenResponse);

        // Mock the response code from the server
        when(mockConnection.getResponseCode()).thenReturn(200);

        // Mock the response from the server (the response body for testing)
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream("response".getBytes()));

        // Call send with a mock request payload
        byte[] response = client.send("request-payload".getBytes());

        // Verify the token was generated for the first time and attached to the request
        verify(mockConnection).setRequestProperty("Authorization", "Bearer mock-token");

        // Assert the response from the server is handled correctly
        assertEquals("response", new String(response));
    }

    @Test
    public void testSend_TokenRefreshedBeforeExpiry() throws Exception {
        // Set token expiry time to be close to the current time, so it will trigger a refresh
        client.tokenExpiryTimeMs = System.currentTimeMillis() + 1 * 1000; // 1 second until expiry

        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "refreshed-token");
        tokenResponse.put("expires_in", "3600");

        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(tokenResponse);

        // Mock the response code from the server
        when(mockConnection.getResponseCode()).thenReturn(200);

        // Mock the response from the server (the response body for testing)
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream("response".getBytes()));

        // Call send with a mock request payload
        byte[] response = client.send("request-payload".getBytes());

        // Verify the token was refreshed and attached to the request
        verify(mockConnection).setRequestProperty("Authorization", "Bearer refreshed-token");

        // Assert the response from the server is handled correctly
        assertEquals("response", new String(response));
    }

    @Test
    public void testSend_RefreshTokenFails() throws Exception {
        // Simulate a SQL exception when trying to refresh the token
        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString())).thenThrow(
                new SQLException("Failed to refresh token"));

        // Expect a RuntimeException to be thrown when send() is called
        try {
            client.send("request-payload".getBytes());
            fail("Expected RuntimeException due to token refresh failure");
        } catch (RuntimeException e) {
            // Assert that the exception message contains the correct information
            assertTrue(e.getMessage().contains("Failed to generate or refresh JWT token"));
        }
    }

    @Test
    public void testSend_IOError() throws Exception {
        // Prepare a mock token response
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "mock-token");
        tokenResponse.put("expires_in", "3600");

        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(tokenResponse);

        // Mock the response code from the server to simulate the request execution
        when(mockConnection.getResponseCode()).thenReturn(200);

        // Simulate an IOException when trying to send the request
        when(mockConnection.getOutputStream()).thenThrow(new IOException("Failed to send request"));

        // Expect a RuntimeException to be thrown due to the IOException
        try {
            client.send("request-payload".getBytes());
            fail("Expected RuntimeException due to IOException");
        } catch (RuntimeException e) {
            // Assert that the exception message contains the correct information
            assertTrue(e.getMessage().contains("Failed to send request"));
        }
    }

    @Test
    public void testSend_403UnauthorizedError() throws Exception {
        // Mock the auth service to provide a sample token
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "sample-token");
        tokenResponse.put("expires_in", "3600");

        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(tokenResponse);

        // Simulate a 403 Unauthorized response from the server
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
        when(mockConnection.getErrorStream()).thenReturn(new ByteArrayInputStream(ERROR_MESSAGE.getBytes()));

        try {
            client.send("request-payload".getBytes());
            fail("Expected RuntimeException due to 403 Unauthorized error");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Client error occurred: HTTP 403"));
            assertTrue(e.getMessage().contains(ERROR_MESSAGE));
        }
    }

    @Test
    public void testSend_MissingToken() throws Exception {
        // Simulate failure to refresh token by throwing an exception in refreshToken
        doThrow(new SQLException("Failed to refresh token due to missing credentials")).when(mockAuthService).getAMAccessToken(anyString(),
                anyString(), anyString(), anyString());

        try {
            client.send("request-payload".getBytes());
            fail("Expected RuntimeException due to missing token");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to generate or refresh JWT token"));
        }
    }
}
