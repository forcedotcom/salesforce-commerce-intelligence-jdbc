package com.salesforce.commerce.intelligence.jdbc.client;

import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;
import org.apache.calcite.avatica.remote.AvaticaCommonsHttpClientImpl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Custom HTTP client for Avatica, which handles JWT-based OAuth2 authentication. It manages the lifecycle of a token, including generating
 * the token when needed, refreshing it before expiry, and injecting the Authorization header into every request.
 */
public class CIPAvaticaHttpClient extends AvaticaCommonsHttpClientImpl {

    private static final long TOKEN_EXPIRY_THRESHOLD = 5 * 60 * 1000; // Refresh token 5 minutes before expiry

    private String jwtToken;
    long tokenExpiryTime = 0; // Initialize tokenExpiryTime to 0 for first-time token generation
    private final AmAuthService amAuthService;
    private final URL url;

    // OAuth2 parameters
    private final String amOauthHost;
    private final String clientId;
    private final String clientSecret;
    private final String instanceId;

    /**
     * Constructor for CustomAvaticaHttpClient. Initializes the client and fetches OAuth2 parameters from the connection properties.
     *
     * @param url The URL to the Avatica server.
     */
    public CIPAvaticaHttpClient(URL url) {
        super(url);
        this.url = url;
        this.amAuthService = new AmAuthService();

        // Retrieve OAuth2 parameters from connection properties
        Properties info = CIPDriver.connectionProperties.get();
        this.amOauthHost = info.getProperty("amOauthHost");
        this.clientId = info.getProperty("user");
        this.clientSecret = info.getProperty("password");
        this.instanceId = info.getProperty("instanceId");
    }

    // constructor to accept AmAuthService for testing
    public CIPAvaticaHttpClient(URL url, AmAuthService amAuthService) {
        super(url);
        this.url = url;
        this.amAuthService = amAuthService; // Injected service

        Properties info = CIPDriver.connectionProperties.get();
        this.amOauthHost = info.getProperty("amOauthHost");
        this.clientId = info.getProperty("user");
        this.clientSecret = info.getProperty("password");
        this.instanceId = info.getProperty("instanceId");
    }

    /**
     * Sends an HTTP request to the Avatica server. Generates the JWT token if not present or refreshes it if it is about to expire, then
     * attaches the token and instance ID in the request headers.
     *
     * @param request The HTTP request body as a byte array.
     * @return The response from the server as a byte array.
     */
    @Override
    public byte[] send(byte[] request) {
        // Generate or refresh the JWT token if it hasn't been generated yet or if it's nearing expiration
        if (isTokenExpiredOrMissing()) {
            try {
                refreshToken();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to generate or refresh JWT token", e);
            }
        }

        // Send the request with updated headers
        try {
            HttpURLConnection connection = createHttpURLConnection();
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken); // Attach Authorization header
            connection.setRequestProperty("InstanceId", instanceId); // Attach InstanceId header

            sendRequestPayload(connection, request);
            return readResponse(connection);
        } catch (IOException e) {
            throw new RuntimeException("Error sending request to Avatica server", e);
        }
    }

    /**
     * Creates and configures an HTTP connection for sending requests to the Avatica server.
     *
     * @return Configured HttpURLConnection object.
     */
    private HttpURLConnection createHttpURLConnection() {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            return connection;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create HTTP connection", e);
        }
    }

    /**
     * Sends the request payload to the server.
     *
     * @param connection The HTTP connection.
     * @param request The request payload as a byte array.
     * @throws IOException If an I/O error occurs during the write.
     */
    private void sendRequestPayload(HttpURLConnection connection, byte[] request) throws IOException {
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(request);
        }
    }

    /**
     * Reads the server's response.
     *
     * @param connection The HTTP connection.
     * @return The response as a byte array.
     * @throws IOException If an I/O error occurs during the read.
     */
    private byte[] readResponse(HttpURLConnection connection) throws IOException {
        return connection.getInputStream().readAllBytes();
    }

    /**
     * Checks if the JWT token is expired or has not been generated yet.
     *
     * @return True if the token is expired or missing, otherwise false.
     */
    private boolean isTokenExpiredOrMissing() {
        // Token needs to be generated for the first time (tokenExpiryTime == 0)
        // or refreshed when close to expiry
        return tokenExpiryTime == 0 || System.currentTimeMillis() >= tokenExpiryTime - TOKEN_EXPIRY_THRESHOLD;
    }

    /**
     * Refreshes the JWT token by contacting the authentication service.
     *
     * @throws SQLException If the token refresh process fails.
     */
    private void refreshToken() throws SQLException {
        // Call the authentication service to obtain a new token
        Map<String, String> tokenResponse = amAuthService.getAMAccessToken(amOauthHost, clientId, clientSecret, instanceId);
        jwtToken = tokenResponse.get("access_token");
        tokenExpiryTime = System.currentTimeMillis() + (Long.parseLong(tokenResponse.get("expires_in")) * 1000); // Set new expiration
    }
}
