package com.salesforce.commerce.intelligence.jdbc.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
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

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;
import org.apache.calcite.avatica.ConnectionConfig;
import org.apache.calcite.avatica.Meta;
import org.apache.calcite.avatica.remote.ProtobufTranslation;
import org.apache.calcite.avatica.remote.Service;
import org.apache.calcite.avatica.remote.TypedValue;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.Before;
import org.junit.Test;

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

    private static final long TOKEN_EXPIRY_THRESHOLD_MS = 5 * 60 * 1000;

    @Before
    public void setUp()
        throws Exception
    {

        // Real connection manager for the HTTP client
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

        // Real request config
        ConnectionConfig config = mock( ConnectionConfig.class );
        when( config.getHttpConnectionTimeout() ).thenReturn( 5000L ); // 5 seconds for the connection timeout
        when( config.getHttpResponseTimeout() ).thenReturn( 30000L ); // 30 seconds for the response timeout

        // Set mock properties
        Properties properties = new Properties();
        properties.put( "amOauthHost", "mock-host" );
        properties.put( "user", "mock-user" );
        properties.put( "password", "mock-password" );
        properties.put( "instanceId", "mock-instance" );

        // Mock the connection properties being used by the CIPDriver class
        CIPDriver.connectionProperties = new ThreadLocal<>();
        CIPDriver.connectionProperties.set( properties );

        // Mock the AmAuthService
        mockAuthService = mock( AmAuthService.class );

        // Mock the ProtobufTranslation
        mockProtobufTranslation = mock( ProtobufTranslation.class );

        // Create a real OpenConnectionRequest with a fixed connectionId
        Service.OpenConnectionRequest realRequest = new Service.OpenConnectionRequest( EXPECTED_CONNECTION_ID, null );

        // Simulate parsing request to return the real request
        when( mockProtobufTranslation.parseRequest( any() ) ).thenReturn( realRequest );

        cipAvaticaHttpClient =
            new CIPAvaticaHttpClient( new URL( "http://127.0.0.1" ), mockAuthService, mockProtobufTranslation );
        cipAvaticaHttpClient.setHttpClientPool( connectionManager, config );
        // Mock the HTTP response behavior
        CloseableHttpResponse mockResponse = mock( CloseableHttpResponse.class );
        when( mockResponse.getCode() ).thenReturn( 200 );
        // Mock the response entity with content
        ByteArrayEntity entity = new ByteArrayEntity( "response".getBytes(), ContentType.APPLICATION_OCTET_STREAM );
        when( mockResponse.getEntity() ).thenReturn( entity );
        // Mock the client's execute method to return the mocked response
        cipAvaticaHttpClient.client = mock( CloseableHttpClient.class );
        when( cipAvaticaHttpClient.client.execute( any( HttpPost.class ), any( HttpClientContext.class ) ) ).thenReturn(
            mockResponse );
    }

    @Test
    public void testSend_GenerateTokenFirstTime()
        throws Exception
    {
        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put( "access_token", "mock-token" );
        tokenResponse.put( "expires_in", "3600" );

        when( mockAuthService.getAMAccessToken( anyString(), anyString(), anyString(), anyString() ) ).thenReturn(
            tokenResponse );

        // Mock HTTP response behavior
        CloseableHttpResponse mockResponse = mock( CloseableHttpResponse.class );
        when( mockResponse.getCode() ).thenReturn( 200 );
        when( mockResponse.getEntity() ).thenReturn( null );

        // Call send with a mock request payload
        byte[] response = cipAvaticaHttpClient.send( MOCK_REQUEST_PAYLOAD.getBytes() );

        // Assert the response content
        assertNotNull( "Response should not be null.", response );
        assertEquals( "The response should match the mocked response content.", new String( response ), "response" );
    }

    @Test
    public void testSend_SessionIdHandling()
        throws Exception
    {
        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put( "access_token", "mock-token" );
        tokenResponse.put( "expires_in", "3600" );

        when( mockAuthService.getAMAccessToken( anyString(), anyString(), anyString(), anyString() ) ).thenReturn(
            tokenResponse );

        // Simulate sessionStore behavior for session ID
        CIPAvaticaHttpClient.sessionStore.put( CONNECTION_ID, MOCK_SESSION_ID );

        // Mock response code and session header
        CloseableHttpResponse mockResponse = mock( CloseableHttpResponse.class );
        when( mockResponse.getCode() ).thenReturn( 200 );
        when( mockResponse.getEntity() ).thenReturn( null );

        // Spy on the CIPAvaticaHttpClient
        CIPAvaticaHttpClient spyClient = spy( cipAvaticaHttpClient );

        // Override the behavior of getHttpPost to capture the HttpPost object
        doAnswer( invocation -> {
            byte[] request = invocation.getArgument( 0 );
            String sessionId = invocation.getArgument( 1 );

            // Create a real HttpPost and verify its behavior
            HttpPost post = (HttpPost) invocation.callRealMethod();
            assertEquals( "Session ID header should match the mocked session ID.", MOCK_SESSION_ID,
                post.getFirstHeader( HEADER_SESSION_ID ).getValue() );

            // Verify Authorization header
            Header authorizationHeader = post.getFirstHeader( "Authorization" );
            assertNotNull( "Authorization header should not be null.", authorizationHeader );
            assertEquals( "Authorization header should match the mocked token.", "Bearer mock-token",
                authorizationHeader.getValue() );

            return post;
        } ).when( spyClient ).getHttpPost( any( byte[].class ), anyString() );

        // Call send method
        spyClient.send( MOCK_REQUEST_PAYLOAD.getBytes() );

        // Verify the getHttpPost method was called with the correct arguments
        verify( spyClient ).getHttpPost( any( byte[].class ), eq( MOCK_SESSION_ID ) );
    }

    @Test
    public void testSend_TokenRefreshedBeforeExpiry()
        throws Exception
    {
        // Set token expiry time to be close to the current time, so it will trigger a refresh
        cipAvaticaHttpClient.tokenExpiryTimeMs = System.currentTimeMillis() + 1 * 1000; // 1 second until expiry

        // Prepare a mock token response from the authentication service
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put( "access_token", "mock-token" );
        tokenResponse.put( "expires_in", "3600" );

        when( mockAuthService.getAMAccessToken( anyString(), anyString(), anyString(), anyString() ) ).thenReturn(
            tokenResponse );

        // Simulate sessionStore behavior for session ID
        CIPAvaticaHttpClient.sessionStore.put( CONNECTION_ID, MOCK_SESSION_ID );

        // Mock response code and session header
        CloseableHttpResponse mockResponse = mock( CloseableHttpResponse.class );
        when( mockResponse.getCode() ).thenReturn( 200 );
        when( mockResponse.getEntity() ).thenReturn( null );

        // Spy on the CIPAvaticaHttpClient
        CIPAvaticaHttpClient spyClient = spy( cipAvaticaHttpClient );

        // Override the behavior of getHttpPost to capture the HttpPost object
        doAnswer( invocation -> {
            // Create a real HttpPost and verify its behavior
            HttpPost post = (HttpPost) invocation.callRealMethod();

            // Verify Authorization header
            Header authorizationHeader = post.getFirstHeader( "Authorization" );
            assertNotNull( "Authorization header should not be null.", authorizationHeader );
            assertEquals( "Authorization header should match the mocked token.", "Bearer mock-token",
                authorizationHeader.getValue() );

            return post;
        } ).when( spyClient ).getHttpPost( any( byte[].class ), anyString() );

        // Call send method
        spyClient.send( MOCK_REQUEST_PAYLOAD.getBytes() );

        // Verify the getHttpPost method was called with the correct arguments
        verify( spyClient ).getHttpPost( any( byte[].class ), eq( MOCK_SESSION_ID ) );
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
    public void testSend_403UnauthorizedError()
        throws Exception
    {
        // Mock the auth service to provide a sample token
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put( "access_token", "sample-token" );
        tokenResponse.put( "expires_in", "3600" );

        when( mockAuthService.getAMAccessToken( anyString(), anyString(), anyString(), anyString() ) ).thenReturn(
            tokenResponse );

        // Mock the HTTP response to simulate a 403 Forbidden error
        CloseableHttpResponse mockResponse = mock( CloseableHttpResponse.class );
        when( mockResponse.getCode() ).thenReturn( 403 ); // HTTP 403 Forbidden
        ByteArrayEntity errorEntity = new ByteArrayEntity( "Unauthorized Access".getBytes(), ContentType.TEXT_PLAIN );
        when( mockResponse.getEntity() ).thenReturn( errorEntity );

        // Mock the client's execute method to return the mocked response
        when( cipAvaticaHttpClient.client.execute( any( HttpPost.class ), any( HttpClientContext.class ) ) ).thenReturn(
            mockResponse );

        try
        {
            // Call the send method
            cipAvaticaHttpClient.send( "request-payload".getBytes() );
            fail( "Expected RuntimeException due to 403 Unauthorized error" );
        }
        catch ( RuntimeException e )
        {
            // Verify that the exception contains the correct message
            assertTrue( "Exception message should contain HTTP 403.",
                e.getMessage().contains( "HTTP request failed with status code 403" ) );
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

    @Test
    public void testExtractConnectionId_NoSuchFieldException() {
        // Mock a request object with no "connectionId" field
        Service.Request mockRequest = mock(Service.Request.class);

        // Extract connection ID
        String connectionId = cipAvaticaHttpClient.extractConnectionId(mockRequest);

        // Assert that connectionId is null when the field does not exist
        assertEquals("Connection ID should be null when field is missing.", null, connectionId);
    }

    @Test
    public void testExtractConnectionId_ExecuteRequest() {
        // Create a real Meta.StatementHandle with a mock connectionId
        Meta.StatementHandle realStatementHandle = new Meta.StatementHandle("mock-connection-id", 123, null);

        // Create a dummy parameterValues list (can be empty)
        List<TypedValue> parameterValues = Collections.emptyList();

        // Set a dummy maxRowCount
        int maxRowCount = 100;

        // Create a real Service.ExecuteRequest instance
        Service.ExecuteRequest realRequest = new Service.ExecuteRequest(realStatementHandle, parameterValues, maxRowCount);

        // Extract connection ID
        String connectionId = cipAvaticaHttpClient.extractConnectionId(realRequest);

        // Assert that the correct connectionId is extracted
        assertEquals("mock-connection-id", connectionId);
    }

    @Test
    public void testSend_ParseRequestIOException() throws Exception {
        // Simulate an IOException when parsing the request
        when(mockProtobufTranslation.parseRequest(any())).thenThrow(new IOException("Failed to parse request"));

        try {
            // Call the send method
            cipAvaticaHttpClient.send("request-payload".getBytes());
            fail("Expected RuntimeException due to IOException during request parsing");
        } catch (RuntimeException e) {
            // Verify that the exception contains the correct message
            assertTrue(e.getMessage().contains("Failed to parse request"));
        }
    }

    @Test
    public void testSend_RetryOn503()
        throws Exception
    {
        Map<String, String> tokenResponse = new HashMap<>();
        tokenResponse.put( "access_token", "mock-token" );
        tokenResponse.put( "expires_in", "3600" );
        when( mockAuthService.getAMAccessToken( anyString(), anyString(), anyString(), anyString() ) ).thenReturn(
            tokenResponse );

        CIPAvaticaHttpClient.sessionStore.put( CONNECTION_ID, MOCK_SESSION_ID );

        CloseableHttpResponse mock503Response = mock( CloseableHttpResponse.class );
        when( mock503Response.getCode() ).thenReturn( 503 );
        when( mock503Response.getEntity() ).thenReturn( null );

        CloseableHttpResponse mockSuccessResponse = mock( CloseableHttpResponse.class );
        when( mockSuccessResponse.getCode() ).thenReturn( 200 );
        when( mockSuccessResponse.getEntity() ).thenReturn(
            new ByteArrayEntity( MOCK_RESPONSE_PAYLOAD.getBytes(), ContentType.APPLICATION_OCTET_STREAM ) );

        when( cipAvaticaHttpClient.client.execute( any( HttpPost.class ), any( HttpClientContext.class ) ) ).thenReturn(
            mock503Response ).thenReturn( mockSuccessResponse );

        byte[] response = cipAvaticaHttpClient.send( MOCK_REQUEST_PAYLOAD.getBytes() );
        assertEquals( "response", new String( response ) );
    }

    @Test
    public void testIsTokenExpiredOrMissing() throws Exception {
        // fudge factor for timing
        cipAvaticaHttpClient.tokenExpiryTimeMs = System.currentTimeMillis() + TOKEN_EXPIRY_THRESHOLD_MS + 5000;
        java.lang.reflect.Method m = cipAvaticaHttpClient.getClass().getDeclaredMethod("isTokenExpiredOrMissing");
        m.setAccessible(true);
        boolean result1 = (boolean) m.invoke(cipAvaticaHttpClient);
        assertFalse("Token should not be considered expired.", result1);

        cipAvaticaHttpClient.tokenExpiryTimeMs = System.currentTimeMillis() + 1000;
        boolean result2 = (boolean) m.invoke(cipAvaticaHttpClient);
        assertTrue("Token should be considered near expiry.", result2);
    }

    @Test
    public void testSend_InTestMode_UsesStaticTokenAndSkipsRefresh() throws Exception {
        // Set up properties to enable test mode
        Properties properties = new Properties();
        properties.put("amOauthHost", "mock-host");
        properties.put("user", "mock-user");
        properties.put("password", "mock-password");
        properties.put("instanceId", "mock-instance");
        properties.put("testMode", "true");
        CIPDriver.connectionProperties.set(properties);

        // Use real constructor to trigger testMode logic
        CIPAvaticaHttpClient testModeClient = new CIPAvaticaHttpClient(new URL("http://127.0.0.1"), mockAuthService,
                mockProtobufTranslation);
        testModeClient.client = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(
                new ByteArrayEntity(MOCK_RESPONSE_PAYLOAD.getBytes(), ContentType.APPLICATION_OCTET_STREAM));
        when(testModeClient.client.execute(any(HttpPost.class), any(HttpClientContext.class))).thenReturn(mockResponse);

        // Call send and verify the static token is used
        byte[] response = testModeClient.send(MOCK_REQUEST_PAYLOAD.getBytes());
        assertEquals(MOCK_RESPONSE_PAYLOAD, new String(response));
        // The static token should be set
        assertNotNull(testModeClient);
        java.lang.reflect.Field tokenField = testModeClient.getClass().getDeclaredField("jwtToken");
        tokenField.setAccessible(true);
        assertNotNull(tokenField.get(testModeClient));
        // The auth service should NOT be called in test mode
        verify(mockAuthService, org.mockito.Mockito.never()).getAMAccessToken(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testIsTokenExpiredOrMissing_InTestMode() throws Exception {
        // Set up properties to enable test mode
        Properties properties = new Properties();
        properties.put("amOauthHost", "mock-host");
        properties.put("user", "mock-user");
        properties.put("password", "mock-password");
        properties.put("instanceId", "mock-instance");
        properties.put("testMode", "true");
        CIPDriver.connectionProperties.set(properties);

        CIPAvaticaHttpClient testModeClient = new CIPAvaticaHttpClient(new URL("http://127.0.0.1"), mockAuthService,
                mockProtobufTranslation);
        // Should be false since static token is set
        java.lang.reflect.Method m = testModeClient.getClass().getDeclaredMethod("isTokenExpiredOrMissing");
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(testModeClient);
        assertFalse(result);
        // Now set token to null and check
        java.lang.reflect.Field tokenField = testModeClient.getClass().getDeclaredField("jwtToken");
        tokenField.setAccessible(true);
        tokenField.set(testModeClient, null);
        boolean result2 = (boolean) m.invoke(testModeClient);
        assertTrue(result2);
    }

    @Test
    public void testClientVersionLoadedFromProperties() throws Exception {
        // Access the private static field via reflection
        java.lang.reflect.Field versionField = CIPAvaticaHttpClient.class.getDeclaredField("clientVersion");
        versionField.setAccessible(true);
        String version = (String) versionField.get(null);

        // The value should not be the fallback or unresolved value
        assertNotNull(version);
        assertFalse(version.isEmpty());
        assertFalse("@project.version@".equals(version));
        assertFalse("unknown".equals(version));
    }
}