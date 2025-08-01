package com.salesforce.commerce.intelligence.jdbc.client;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;
import org.apache.calcite.avatica.ConnectionConfig;
import org.apache.calcite.avatica.remote.AvaticaHttpClient;
import org.apache.calcite.avatica.remote.HttpClientPoolConfigurable;
import org.apache.calcite.avatica.remote.ProtobufTranslation;
import org.apache.calcite.avatica.remote.ProtobufTranslationImpl;
import org.apache.calcite.avatica.remote.Service;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom implementation of AvaticaHttpClient that handles JWT-based OAuth2 authentication. It manages the lifecycle of a token, including
 * generating the token when needed, refreshing it before expiry, and injecting the Authorization header into every request sent to the
 * Avatica server.
 */

public class CIPAvaticaHttpClient
                implements AvaticaHttpClient, HttpClientPoolConfigurable
{
    // Refresh the token 5 minutes before expiry
    private static final long TOKEN_EXPIRY_THRESHOLD_MS = 5 * 60 * 1000;

    private static final String HEADER_SESSION_ID = "x-session-id";
    private static final String HEADER_REQUEST_TYPE = "x-Request-Type";
    private static final String HEADER_REQUEST_QUERY_EXECUTE = "query-execute";

    /**
     * A fake JWT access token used in test mode to simulate authentication.
     * This token is used when testMode is enabled to avoid making actual OAuth calls
     * to the authentication service. It allows for testing the client's functionality
     * without requiring valid credentials or network connectivity to the auth server.
     * 
     * The token is required because the server-side CIPAvaticaHandler performs validation
     * that requires a non-null, non-empty token in the Authorization header. If no token
     * is provided, the server will return a 401 Unauthorized error. This fake token
     * allows test mode to bypass this validation while still maintaining the expected
     * request structure.
     */
    private static final String FAKE_TK = "fake_tk";
    private String jwtToken; // The current JWT token for authorization
    long tokenExpiryTimeMs = 0; // Timestamp (in ms) when the token expires
    private final AmAuthService amAuthService; // Service for handling OAuth2 authentication
    private final ProtobufTranslation pbTranslation;

    // Test mode fields
    // This is used to simulate authentication for testing purposes.
    // It is set to true if the testMode property is set to true in the connection properties.
    // If testMode is true, the jwtToken and tokenExpiryTimeMs are set to the fake access token and 1 hour from now.
    // This is used to avoid making actual OAuth calls to the authentication service.
    private final boolean testMode;

    // OAuth2 parameters
    private final String oauthHost;
    private final String clientId;
    private final String clientSecret;
    private final String instanceId;

    private static final Logger LOG = LoggerFactory.getLogger(CIPAvaticaHttpClient.class);

    // Thread-safe store for session IDs associated with each connection
    // --------------------------------------------------------------------
    // The `sessionStore` is a ConcurrentHashMap that maps unique connection IDs
    // to session IDs. Each entry in the map corresponds to a unique connection
    // initiated by this client.
    //
    // Key: `connectionId` (String) - a unique identifier for the client connection.
    // Value: `sessionId` (String) - the session ID associated with the client
    // connection. This session ID is used to maintain stickiness for the session
    // across requests.
    //
    // This map is thread-safe to support concurrent access in environments where
    // multiple connections may be established in parallel. It is used primarily
    // to set the `x-session-id` HTTP header on outgoing requests, ensuring that
    // requests from the same connection are routed to the correct server instance
    // by the load balancer (for instance, using Istio or other session-aware
    // routing systems).
    static final ConcurrentHashMap<String, String> sessionStore = new ConcurrentHashMap<>();
    protected final URI uri; // uri of avatica server

    protected CloseableHttpClient client;

    protected HttpClientContext context;

    private static String loadClientVersion() {
        try (java.io.InputStream is = CIPAvaticaHttpClient.class.getClassLoader().getResourceAsStream("version.properties")) {
            java.util.Properties props = new java.util.Properties();
            if (is != null) {
                props.load(is);
                return props.getProperty("version", "unknown");
            }
        } catch (java.io.IOException e) {
            // fallback to "unknown"
        }
        return "unknown";
    }

    private static String clientVersion = loadClientVersion();

    public CIPAvaticaHttpClient( URL url) {
        this.uri = toURI((URL)Objects.requireNonNull(url));
        this.amAuthService = new AmAuthService();
        this.pbTranslation = new ProtobufTranslationImpl();

        // Retrieve OAuth2 parameters from connection properties
        Properties connectionProps = CIPDriver.connectionProperties.get();
        this.oauthHost = connectionProps.getProperty("amOauthHost");
        this.clientId = connectionProps.getProperty("user");
        this.clientSecret = connectionProps.getProperty("password");
        this.instanceId = connectionProps.getProperty("instanceId");
        
        // Initialize test mode fields
        this.testMode = Boolean.parseBoolean(connectionProps.getProperty("testMode", "false"));
        if (this.testMode) {
            this.jwtToken = FAKE_TK;  
            this.tokenExpiryTimeMs = System.currentTimeMillis() + 3600000L;
        }
    }

    /**
     * Constructor for CIPAvaticaHttpClient used for testing with a mock authentication service.
     *
     * @param url The URL of the Avatica server.
     * @param amAuthService The authentication service used to get and refresh tokens.
     */
    public CIPAvaticaHttpClient( URL url, AmAuthService amAuthService, ProtobufTranslation pbTranslation) {
        this.uri = toURI((URL)Objects.requireNonNull(url));
        this.amAuthService = amAuthService;
        this.pbTranslation = pbTranslation;

        // Retrieve OAuth2 parameters from connection properties
        Properties connectionProps = CIPDriver.connectionProperties.get();
        this.oauthHost = connectionProps.getProperty("amOauthHost");
        this.clientId = connectionProps.getProperty("user");
        this.clientSecret = connectionProps.getProperty("password");
        this.instanceId = connectionProps.getProperty("instanceId");
        
        // Initialize test mode fields
        this.testMode = Boolean.parseBoolean(connectionProps.getProperty("testMode", "false"));
        if (this.testMode) {
            this.jwtToken = FAKE_TK;
            this.tokenExpiryTimeMs = System.currentTimeMillis() + 3600000L;
        }
    }

    protected void initializeClient(PoolingHttpClientConnectionManager pool, ConnectionConfig config) {
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        RequestConfig requestConfig = requestConfigBuilder.setConnectTimeout(config.getHttpConnectionTimeout(), TimeUnit.MILLISECONDS).setResponseTimeout( config.getHttpResponseTimeout(), TimeUnit.MILLISECONDS ).build();
        // Use system properties to support proxy, SSL, and timeout configurations
        // This allows the driver to respect standard Java settings like:
        // - http.proxyHost / http.proxyPort
        // - https.proxyHost / https.proxyPort
        // - javax.net.ssl.trustStore / trustStorePassword
        // It helps the driver work correctly in environments with custom network setups (e.g., behind a proxy)
        HttpClientBuilder httpClientBuilder = HttpClients.custom().useSystemProperties().setConnectionManager(pool).setDefaultRequestConfig(requestConfig);
        this.context = HttpClientContext.create();
        this.client = httpClientBuilder.build();
    }

    public byte[] send(byte[] request) {
        LOG.debug("Sending request to Avatica server.");

        Service.Request genericReq = getGenericReq(request);
        String connectionId = logAndExtractConnectionId(genericReq);
        String sessionId = sessionStore.get(connectionId);

        refreshJwtIfNeeded();
        int maxRetries = 5;
        int attempt = 0;
        while ( attempt < maxRetries ) {
            HttpClientContext httpContext = HttpClientContext.create();
            HttpPost post = getHttpPost(request, sessionId);

            try (CloseableHttpResponse response = this.execute(post, httpContext)) {
                byte[] result = handleResponse(response, genericReq, connectionId);
                if (result.length == 0) {
                    attempt++;
                    LOG.warn("Empty response, retry attempt {}", attempt);
                    continue; // retry on empty array (e.g. 503)
                }
                return result;
            } catch (NoHttpResponseException e) {
                LOG.debug("The Avatica server failed to issue an HTTP response, retrying");
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                LOG.debug("Failed to execute HTTP request", e);
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Max retry attempts reached for 503 responses.");
    }

    private String logAndExtractConnectionId(Service.Request genericReq) {
        String connectionId = extractConnectionId(genericReq);
        if (connectionId != null) {
            LOG.debug("Extracted Connection ID: {}", connectionId);
        } else {
            LOG.warn("Unable to extract Connection ID for request type: {}", genericReq.getClass().getSimpleName());
        }
        return connectionId;
    }

    private void refreshJwtIfNeeded() {
        if (isTokenExpiredOrMissing()) {
            try {
                LOG.debug("Refreshing JWT token.");
                refreshToken();
                LOG.debug("JWT token refreshed.");
            } catch (SQLException e) {
                LOG.error("Failed to generate or refresh JWT token.", e);
                throw new RuntimeException("Failed to generate or refresh JWT token.", e);
            }
        }
    }

    private byte[] handleResponse(CloseableHttpResponse response, Service.Request genericReq, String connectionId) throws Exception {
        int statusCode = response.getCode();

        if (statusCode == HttpURLConnection.HTTP_OK) {
            return handleSuccessResponse(response, genericReq, connectionId);
        } else if (statusCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
            handleInternalServerError(response);
        } else if (statusCode == 503) {
            LOG.warn("Failed to connect to server (HTTP/503), retrying");
            return new byte[0];
        } else {
            handleUnexpectedStatus(response, statusCode);
        }
        return new byte[0];
    }

    private byte[] handleSuccessResponse(CloseableHttpResponse response, Service.Request genericReq, String connectionId)
                    throws IOException, ProtocolException
    {
        Header sessionHeader = response.getHeader(HEADER_SESSION_ID);
        String newSessionId = (sessionHeader != null) ? sessionHeader.getValue() : null;

        if (newSessionId != null) {
            LOG.debug("Captured new session ID: {}", newSessionId);
            sessionStore.put(connectionId, newSessionId);
        } else if (genericReq instanceof Service.OpenConnectionRequest) {
            LOG.warn("Session ID not provided in response for connection ID: {}", connectionId);
        }
        return EntityUtils.toByteArray(response.getEntity());
    }

    private void handleInternalServerError(CloseableHttpResponse response)
                    throws SQLException, IOException, ParseException
    {
        String errorMessage = (response.getEntity() != null) ?
                        EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) :
                        "No response body.";
        LOG.error("Received 500 Internal Server Error: {}", errorMessage);
        throw new SQLException("Server responded with HTTP 500: Internal Server Error. Details: " + errorMessage);
    }

    private void handleUnexpectedStatus(CloseableHttpResponse response, int statusCode)
                    throws IOException, ParseException
    {
        String responseBody = (response.getEntity() != null) ?
                        EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) :
                        "";
        LOG.error("HTTP request failed with status code {}: {}. Errors: {}", statusCode, response.getReasonPhrase(), responseBody);
        throw new RuntimeException(String.format(
                        "HTTP request failed with status code %d: %s. Errors: %s",
                        statusCode, response.getReasonPhrase(), responseBody
        ));
    }

    HttpPost getHttpPost( byte[] request, String sessionId )
    {
        ByteArrayEntity entity = new ByteArrayEntity( request, ContentType.APPLICATION_OCTET_STREAM);
        HttpPost post = new HttpPost(this.uri);
        post.setEntity(entity);
        post.setHeader("Authorization", "Bearer " + jwtToken);
        post.setHeader("InstanceId", instanceId);  // Attach InstanceId header
        post.setHeader("X-Client-Version", clientVersion);

        // Attach session ID if available
        if ( sessionId != null) {
            post.setHeader(HEADER_SESSION_ID, sessionId );
        }

        // Add executeRequest header
        Service.Request genericReq = getGenericReq( request );
        if ( genericReq instanceof Service.ExecuteRequest ||
             genericReq instanceof Service.PrepareAndExecuteRequest ||
             genericReq instanceof Service.PrepareAndExecuteBatchRequest ||
             genericReq instanceof Service.ExecuteBatchRequest )
        {
            LOG.debug( "Setting {} header to {}", HEADER_REQUEST_TYPE, HEADER_REQUEST_QUERY_EXECUTE );
            post.setHeader( HEADER_REQUEST_TYPE, HEADER_REQUEST_QUERY_EXECUTE );
        }

        return post;
    }

    CloseableHttpResponse execute(HttpPost post, HttpClientContext context) throws IOException, ClientProtocolException {
        return this.client.execute(post, context);
    }

    private static URI toURI(URL url) throws RuntimeException {
        try {
            return url.toURI();
        } catch (URISyntaxException var2) {
            throw new RuntimeException(var2);
        }
    }

    public void setHttpClientPool(PoolingHttpClientConnectionManager pool, ConnectionConfig config) {
        this.initializeClient(pool, config);
    }

    /**
     * Checks if the JWT token is missing or is about to expire.
     *
     * @return True if the token is missing or near expiration, otherwise false.
     */
    private boolean isTokenExpiredOrMissing() {
        if (this.testMode) {
            return this.jwtToken == null;
        }
        boolean isExpiredOrMissing = tokenExpiryTimeMs == 0 || System.currentTimeMillis() >= tokenExpiryTimeMs - TOKEN_EXPIRY_THRESHOLD_MS;
        LOG.debug("Token expired or missing: {}", isExpiredOrMissing);
        return isExpiredOrMissing;
    }

    /**
     * Refreshes the JWT token by contacting the authentication service.
     *
     * @throws SQLException If the token refresh process fails.
     */
    private void refreshToken() throws SQLException {
        LOG.debug("Refreshing JWT token.");
        Map<String, String> tokenResponse = amAuthService.getAMAccessToken(oauthHost, clientId, clientSecret, instanceId);
        jwtToken = tokenResponse.get("access_token");
        tokenExpiryTimeMs = System.currentTimeMillis() + (Long.parseLong(tokenResponse.get("expires_in")) * 1000); // Set new expiration //
        // time
    }

    String extractConnectionId(Service.Request request) {
        try {
            if (request instanceof Service.ExecuteRequest) {
                // Extract connectionId directly from statementHandle
                return ((Service.ExecuteRequest) request).statementHandle.connectionId;
            }

            // Use reflection to find "connectionId" field for other request types
            Field connectionIdField = request.getClass().getDeclaredField("connectionId");
            connectionIdField.setAccessible(true);
            return (String) connectionIdField.get(request);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOG.debug("Unable to extract connectionId for request type {}. Error: {}",
                            request.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    Service.Request getGenericReq( byte[] request )
    {
        Service.Request genericReq;
        try
        {
            genericReq = pbTranslation.parseRequest( request );
        }
        catch ( IOException e )
        {
            LOG.error( "Exception when extracting connection Id:", e );
            throw new RuntimeException( e );
        }
        return genericReq;
    }
}

