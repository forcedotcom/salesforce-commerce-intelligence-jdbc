package com.salesforce.commerce.intelligence.jdbc.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;
import org.apache.calcite.avatica.ConnectionConfig;
import org.apache.calcite.avatica.remote.ProtobufTranslation;
import org.apache.calcite.avatica.remote.Service;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class CIPAvaticaHttpClientTest {
    private static final String HEADER_SESSION_ID = "x-session-id";

    private CIPAvaticaHttpClient cipAvaticaHttpClient;
    private AmAuthService mockAuthService;

    private ProtobufTranslation mockProtobufTranslation;
    private static final String ERROR_MESSAGE = "Unauthorized access";
    private static final String EXPECTED_CONNECTION_ID = "mock-connection-id";

    private static final String MOCK_SESSION_ID = "mock-session-id";
    private static final String CONNECTION_ID = "mock-connection-id";

    private static final String MOCK_REQUEST_PAYLOAD = "request-payload";
    private static final String MOCK_RESPONSE_PAYLOAD = "response";

    @Before
    public void setUp() throws Exception {

        // Real connection manager for the HTTP client
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

        // Real request config
        ConnectionConfig config = mock(ConnectionConfig.class);
        when(config.getHttpConnectionTimeout()).thenReturn( 5000L ); // 5 seconds for the connection timeout
        when(config.getHttpResponseTimeout()).thenReturn( 30000L ); // 30 seconds for the response timeout

        // Set mock properties
        Properties properties = new Properties();
        properties.put("amOauthHost", "mock-host");
        properties.put("user", "mock-user");
        properties.put("password", "mock-password");
        properties.put("instanceId", "mock-instance");

        // Mock the connection properties being used by the CIPDriver class
        CIPDriver.connectionProperties = new ThreadLocal<>();
        CIPDriver.connectionProperties.set(properties);

        // Mock the AmAuthService
        mockAuthService = mock(AmAuthService.class);

        // Mock the ProtobufTranslation
        mockProtobufTranslation = mock(ProtobufTranslation.class);

        // Create a real OpenConnectionRequest with a fixed connectionId
        Service.OpenConnectionRequest realRequest = new Service.OpenConnectionRequest(EXPECTED_CONNECTION_ID, null);

        // Simulate parsing request to return the real request
        when(mockProtobufTranslation.parseRequest(any())).thenReturn(realRequest);

        cipAvaticaHttpClient =
                        new CIPAvaticaHttpClient(new URL("http://127.0.0.1"),mockAuthService, mockProtobufTranslation);
        cipAvaticaHttpClient.setHttpClientPool(connectionManager, config);
        // Mock the HTTP response behavior
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(200);
        // Mock the response entity with content
        ByteArrayEntity entity = new ByteArrayEntity("response".getBytes(), ContentType.APPLICATION_OCTET_STREAM );
        when(mockResponse.getEntity()).thenReturn(entity);
        // Mock the client's execute method to return the mocked response
        cipAvaticaHttpClient.client = mock( CloseableHttpClient.class);
        when(cipAvaticaHttpClient.client.execute(any(HttpPost.class), any( HttpClientContext.class)))
                        .thenReturn(mockResponse);
    }

    @Test
    public void testSend_GenerateTokenFirstTime() throws Exception {
        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "mock-token");
        tokenResponse.put("expires_in", "3600");

        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(tokenResponse);

        // Mock HTTP response behavior
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(null);

        // Call send with a mock request payload
        byte[] response = cipAvaticaHttpClient.send(MOCK_REQUEST_PAYLOAD.getBytes());

        // Assert the response content
        assertNotNull( "Response should not be null.", response);
        assertEquals("The response should match the mocked response content.", new String(response), "response");
    }

    @Test
    public void testSend_SessionIdHandling() throws Exception {
        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "mock-token");
        tokenResponse.put("expires_in", "3600");

        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(tokenResponse);

        // Simulate sessionStore behavior for session ID
        CIPAvaticaHttpClient.sessionStore.put(CONNECTION_ID, MOCK_SESSION_ID);

        // Mock response code and session header
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(null);

        // Spy on the CIPAvaticaHttpClient
        CIPAvaticaHttpClient spyClient = spy(cipAvaticaHttpClient);

        // Override the behavior of getHttpPost to capture the HttpPost object
        doAnswer(invocation -> {
            byte[] request = invocation.getArgument(0);
            String sessionId = invocation.getArgument(1);

            // Create a real HttpPost and verify its behavior
            HttpPost post = (HttpPost) invocation.callRealMethod();
            assertEquals("Session ID header should match the mocked session ID.",MOCK_SESSION_ID, post.getFirstHeader(HEADER_SESSION_ID).getValue());


            // Verify Authorization header
            Header authorizationHeader = post.getFirstHeader("Authorization");
            assertNotNull("Authorization header should not be null.", authorizationHeader);
            assertEquals("Authorization header should match the mocked token.","Bearer mock-token", authorizationHeader.getValue());

            return post;
        }).when(spyClient).getHttpPost(any(byte[].class), anyString());

        // Call send method
        spyClient.send(MOCK_REQUEST_PAYLOAD.getBytes());

        // Verify the getHttpPost method was called with the correct arguments
        verify(spyClient).getHttpPost(any(byte[].class), eq(MOCK_SESSION_ID));
    }

    @Test
    public void testSend_TokenRefreshedBeforeExpiry() throws Exception {
        // Set token expiry time to be close to the current time, so it will trigger a refresh
        cipAvaticaHttpClient.tokenExpiryTimeMs = System.currentTimeMillis() + 1 * 1000; // 1 second until expiry

        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "mock-token");
        tokenResponse.put("expires_in", "3600");

        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(tokenResponse);

        // Simulate sessionStore behavior for session ID
        CIPAvaticaHttpClient.sessionStore.put(CONNECTION_ID, MOCK_SESSION_ID);

        // Mock response code and session header
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(null);

        // Spy on the CIPAvaticaHttpClient
        CIPAvaticaHttpClient spyClient = spy(cipAvaticaHttpClient);

        // Override the behavior of getHttpPost to capture the HttpPost object
        doAnswer(invocation -> {
            // Create a real HttpPost and verify its behavior
            HttpPost post = (HttpPost) invocation.callRealMethod();

            // Verify Authorization header
            Header authorizationHeader = post.getFirstHeader("Authorization");
            assertNotNull( "Authorization header should not be null.", authorizationHeader);
            assertEquals("Authorization header should match the mocked token.", "Bearer mock-token", authorizationHeader.getValue());

            return post;
        }).when(spyClient).getHttpPost(any(byte[].class), anyString());

        // Call send method
        spyClient.send(MOCK_REQUEST_PAYLOAD.getBytes());

        // Verify the getHttpPost method was called with the correct arguments
        verify(spyClient).getHttpPost(any(byte[].class), eq(MOCK_SESSION_ID));
    }

    @Test
    public void testSend_RefreshTokenFails() throws Exception {
        // Simulate a SQL exception when trying to refresh the token
        when(mockAuthService.getAMAccessToken(anyString(), anyString(), anyString(), anyString())).thenThrow(
                new SQLException("Failed to refresh token"));

        // Expect a RuntimeException to be thrown when send() is called
        try {
            cipAvaticaHttpClient.send("request-payload".getBytes());
            fail("Expected RuntimeException due to token refresh failure");
        } catch (RuntimeException e) {
            // Assert that the exception message contains the correct information
            assertTrue(e.getMessage().contains("Failed to generate or refresh JWT token"));
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

        // Mock the HTTP response to simulate a 403 Forbidden error
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(403); // HTTP 403 Forbidden
        ByteArrayEntity errorEntity = new ByteArrayEntity("Unauthorized Access".getBytes(), ContentType.TEXT_PLAIN);
        when(mockResponse.getEntity()).thenReturn(errorEntity);

        // Mock the client's execute method to return the mocked response
        when(cipAvaticaHttpClient.client.execute(any(HttpPost.class), any(HttpClientContext.class)))
                        .thenReturn(mockResponse);

        try {
            // Call the send method
            cipAvaticaHttpClient.send("request-payload".getBytes());
            fail("Expected RuntimeException due to 403 Unauthorized error");
        } catch (RuntimeException e) {
            // Verify that the exception contains the correct message
            assertTrue("Exception message should contain HTTP 403.", e.getMessage().contains("HTTP/403"));
        }
    }

    @Test
    public void testSend_MissingToken() throws Exception {
        // Simulate failure to refresh token by throwing an exception in refreshToken
        doThrow(new SQLException("Failed to refresh token due to missing credentials")).when(mockAuthService).getAMAccessToken(anyString(),
                anyString(), anyString(), anyString());

        try {
            cipAvaticaHttpClient.send("request-payload".getBytes());
            fail("Expected RuntimeException due to missing token");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to generate or refresh JWT token"));
        }
    }
}
