package com.salesforce.commerce.intelligence.jdbc.client;

import com.salesforce.commerce.intelligence.jdbc.client.auth.AmAuthService;
import org.apache.calcite.avatica.AvaticaUtils;
import org.apache.calcite.avatica.remote.AvaticaHttpClientImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Custom implementation of AvaticaHttpClient that handles JWT-based OAuth2 authentication.
 * It manages the lifecycle of a token, including generating the token when needed, refreshing
 * it before expiry, and injecting the Authorization header into every request sent to the Avatica server.
 */
public class CIPAvaticaHttpClient extends AvaticaHttpClientImpl {

    // Refresh the token 5 minutes before expiry
    private static final long TOKEN_EXPIRY_THRESHOLD_MS = 5 * 60 * 1000;

    public long tokenExpiryTime;

    private String jwtToken;          // The current JWT token for authorization
    private long tokenExpiryTimeMs = 0; // Timestamp (in ms) when the token expires

    private final AmAuthService amAuthService;  // Service for handling OAuth2 authentication
    private final URL avaticaUrl;               // URL of the Avatica server

    // OAuth2 parameters
    private final String oauthHost;
    private final String clientId;
    private final String clientSecret;
    private final String instanceId;

    private static final Logger LOG = LogManager.getLogger(CIPAvaticaHttpClient.class);

    /**
     * Constructor for CIPAvaticaHttpClient that initializes OAuth2 parameters from the connection properties.
     *
     * @param url The URL of the Avatica server.
     */
    public CIPAvaticaHttpClient(URL url) {
        super(url);
        this.avaticaUrl = url;
        this.amAuthService = new AmAuthService();

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
    public CIPAvaticaHttpClient(URL url, AmAuthService amAuthService) {
        super(url);
        this.avaticaUrl = url;
        this.amAuthService = amAuthService;

        // Retrieve OAuth2 parameters from connection properties
        Properties connectionProps = CIPDriver.connectionProperties.get();
        this.oauthHost = connectionProps.getProperty("amOauthHost");
        this.clientId = connectionProps.getProperty("user");
        this.clientSecret = connectionProps.getProperty("password");
        this.instanceId = connectionProps.getProperty("instanceId");
    }

    /**
     * Sends an HTTP request to the Avatica server. It generates or refreshes the JWT token if necessary,
     * then attaches the token and instance ID in the request headers before sending the request.
     *
     * @param request The HTTP request body as a byte array.
     * @return The response from the server as a byte array.
     */
    @Override
    public byte[] send(byte[] request) {
        LOG.debug("Sending request to Avatica server.");

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
        tokenExpiryTimeMs = System.currentTimeMillis() + (Long.parseLong(tokenResponse.get("expires_in")) * 1000);  // Set new expiration time
    }
}
