package com.salesforce.commerce.intelligence.jdbc.client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;
import org.apache.calcite.avatica.AvaticaUtils;
import org.apache.calcite.avatica.remote.AvaticaHttpClientImpl;
import org.apache.calcite.avatica.remote.ProtobufTranslation;
import org.apache.calcite.avatica.remote.ProtobufTranslationImpl;
import org.apache.calcite.avatica.remote.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Custom implementation of AvaticaHttpClient that handles JWT-based OAuth2 authentication. It manages the lifecycle of a token, including
 * generating the token when needed, refreshing it before expiry, and injecting the Authorization header into every request sent to the
 * Avatica server.
 */
public class CIPAvaticaHttpClient extends AvaticaHttpClientImpl {

    // Refresh the token 5 minutes before expiry
    private static final long TOKEN_EXPIRY_THRESHOLD_MS = 5 * 60 * 1000;
    private String jwtToken; // The current JWT token for authorization
    long tokenExpiryTimeMs = 0; // Timestamp (in ms) when the token expires
    private final AmAuthService amAuthService; // Service for handling OAuth2 authentication
    private final ProtobufTranslation pbTranslation;
    private final URL avaticaUrl; // URL of the Avatica server

    // OAuth2 parameters
    private final String oauthHost;
    private final String clientId;
    private final String clientSecret;
    private final String instanceId;

    private static final Logger LOG = LogManager.getLogger(CIPAvaticaHttpClient.class);

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

    /**
     * Constructor for CIPAvaticaHttpClient that initializes OAuth2 parameters from the connection properties.
     *
     * @param url The URL of the Avatica server.
     */
    public CIPAvaticaHttpClient(URL url) {
        super(url);
        this.avaticaUrl = url;
        this.amAuthService = new AmAuthService();
        this.pbTranslation = new ProtobufTranslationImpl();

        // Retrieve OAuth2 parameters from connection properties
        Properties connectionProps = CIPDriver.connectionProperties.get();
        this.oauthHost = connectionProps.getProperty("amOauthHost");
        this.clientId = connectionProps.getProperty("user");
        this.clientSecret = connectionProps.getProperty("password");
        this.instanceId = connectionProps.getProperty("instanceId");
    }

    /**
     * Constructor for CIPAvaticaHttpClient used for testing with a mock authentication service.
     *
     * @param url The URL of the Avatica server.
     * @param amAuthService The authentication service used to get and refresh tokens.
     */
    public CIPAvaticaHttpClient(URL url, AmAuthService amAuthService, ProtobufTranslation pbTranslation) {
        super(url);
        this.avaticaUrl = url;
        this.amAuthService = amAuthService;
        this.pbTranslation = pbTranslation;

        // Retrieve OAuth2 parameters from connection properties
        Properties connectionProps = CIPDriver.connectionProperties.get();
        this.oauthHost = connectionProps.getProperty("amOauthHost");
        this.clientId = connectionProps.getProperty("user");
        this.clientSecret = connectionProps.getProperty("password");
        this.instanceId = connectionProps.getProperty("instanceId");
    }

    /**
     * Sends an HTTP request to the Avatica server. It generates or refreshes the JWT token if necessary, then attaches the token and
     * instance ID in the request headers before sending the request.
     *
     * @param request The HTTP request body as a byte array.
     * @return The response from the server as a byte array.
     */
    @Override
    public byte[] send(byte[] request) {
        LOG.debug("Sending request to Avatica server.");

        Service.Request genericReq;
        try
        {
            genericReq = pbTranslation.parseRequest(request);
        }
        catch ( IOException e )
        {
            LOG.error( "Exception when extracting connection Id:", e );
            throw new RuntimeException( e );
        }

        // Attempt to extract connectionId dynamically
        //
        String connectionId = extractConnectionId(genericReq);
        if (connectionId != null) {
            LOG.debug("Extracted Connection ID: {}", connectionId);
        } else {
            LOG.warn("Unable to extract Connection ID for request type: {}", genericReq.getClass().getSimpleName());
        }

        String sessionId = sessionStore.get(connectionId);

        // Check if the JWT token needs to be generated or refreshed
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

        // Retry mechanism to handle HTTP 503 responses (if needed)
        while (true) {
            try {
                HttpURLConnection connection = openConnection();
                connection.setRequestMethod("POST");
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);  // Attach JWT token
                connection.setRequestProperty("InstanceId", instanceId);  // Attach InstanceId header

                // following is not used for now, we are using session id generated by istio instead
                //connection.setRequestProperty("X-Connection-ID", connectionId); //Attach connectionId for the stickiness

                // Attach session ID if available
                if (sessionId != null) {
                    connection.setRequestProperty("x-session-id", sessionId);
                }

                // Send the request payload
                try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                    outputStream.write(request);
                    outputStream.flush();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == 503) {
                    LOG.warn("Received 503 Service Unavailable. Retrying...");
                    continue;  // Retry on 503 response
                }

                // Handle 4xx errors explicitly and capture error details
                if (responseCode >= 400 && responseCode < 500) {
                    String errorMessage = readErrorResponse(connection);
                    LOG.error("Received client error (4xx): HTTP {}. Error: {}", responseCode, errorMessage);
                    throw new RuntimeException("Client error occurred: HTTP " + responseCode + ". Error message: " + errorMessage);
                }

                // Capture session ID from response if available
                String newSessionId = connection.getHeaderField("x-session-id");
                if (newSessionId != null) {
                    sessionStore.put(connectionId, newSessionId);
                    LOG.debug("Captured new session ID: {}", newSessionId);
                }

                // Read response
                InputStream responseStream = (responseCode == 200) ?
                                connection.getInputStream() :
                                connection.getErrorStream();

                if (responseStream == null) {
                    throw new RuntimeException("Failed to read data from the server: HTTP " + responseCode);
                }

                return AvaticaUtils.readFullyToBytes(responseStream);

            } catch (IOException e) {
                LOG.error("Error sending request to Avatica server.", e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Reads the error response from the server's error stream.
     *
     * @param connection The HTTP connection
     * @return The error message from the server's response
     * @throws IOException if an I/O error occurs
     */
    private String readErrorResponse(HttpURLConnection connection) throws IOException {
        try (InputStream errorStream = connection.getErrorStream()) {
            if (errorStream != null) {
                return new String(AvaticaUtils.readFullyToBytes(errorStream), StandardCharsets.UTF_8);
            } else {
                return "No error message returned from the server.";
            }
        }
    }

    /**
     * Opens an HTTP connection to the Avatica server.
     *
     * @return A configured HttpURLConnection object.
     * @throws IOException If an error occurs while opening the connection.
     */
    private HttpURLConnection openConnection() throws IOException {
        return (HttpURLConnection) avaticaUrl.openConnection();
    }

    /**
     * Checks if the JWT token is missing or is about to expire.
     *
     * @return True if the token is missing or near expiration, otherwise false.
     */
    private boolean isTokenExpiredOrMissing() {
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

    /**
     * Dynamically extracts the connectionId field from any Service.Request type.
     *
     * @param request The Service.Request object.
     * @return The connectionId if present, or null otherwise.
     */
    String extractConnectionId( Service.Request request ) {
        try {
            // Use reflection to find "connectionId" field
            java.lang.reflect.Field connectionIdField = request.getClass().getDeclaredField("connectionId");
            connectionIdField.setAccessible(true);
            return (String) connectionIdField.get(request);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOG.debug("Request type {} does not have a connectionId field", request.getClass().getSimpleName());
        }
        return null;
    }
}
