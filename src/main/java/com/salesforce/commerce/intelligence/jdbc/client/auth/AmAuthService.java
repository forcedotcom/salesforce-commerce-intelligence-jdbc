package com.salesforce.commerce.intelligence.jdbc.client.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AmAuthService is responsible for obtaining OAuth tokens from the Account Manager service using client credentials. A single client ID can
 * be associated with multiple instances. However, when requesting an OAuth token, you must specify a single instance name in the scope. For
 * example: "SALESFORCE_COMMERCE_API:bgmj_stg". This allows the service to generate a token specifically for the requested instance,
 * ensuring that the permissions and data access are correctly scoped. Add property amOauthHost = https://account-pod5.demandware.net (to
 * override the AM host name for non-prod)
 */
public class AmAuthService {

    private static final String AM_CLIENT_INFO_BASEPATH = "/dwsso/oauth2/access_token";

    // Default production URL for the OAuth service, can be overridden in the JDBC driver user properties
    private static final String PRD_AM_OAUTH_HOST = "https://account.demandware.com";

    private static final String PRD_AM_OAUTH_URL = PRD_AM_OAUTH_HOST + AM_CLIENT_INFO_BASEPATH;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs an AmAuthService instance and initializes the HTTP client with JSON parser.
     */
    public AmAuthService() {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
    }

    /**
     * Retrieves an OAuth access token using the provided client credentials and instance ID.
     *
     * @param amOAuthHost The base URL for the OAuth service, can be null to use the default.
     * @param amClientId The client ID for authentication.
     * @param amClientSecret The client secret for authentication.
     * @param instanceId The Salesforce Commerce API instance ID.
     * @return The access token as a String, or null if authentication fails.
     * @throws SQLException If the request is unauthorized or has bad credentials.
     */
    public Map<String, String> getAMAccessToken( String amOAuthHost, String amClientId, String amClientSecret, String instanceId )
                    throws SQLException
    {
        // Use the provided OAuth host or fallback to the default
        String tokenEndpoint = ( amOAuthHost != null && !amOAuthHost.isEmpty() ) ?
                        amOAuthHost + AM_CLIENT_INFO_BASEPATH :
                        PRD_AM_OAUTH_URL;

        // Build Authorization header with Base64 encoded client ID and secret
        String authHeader = "Basic " + Base64.getEncoder()
                        .encodeToString( ( amClientId + ":" + amClientSecret ).getBytes( StandardCharsets.UTF_8 ) );

        // Build form-encoded request body
        String formData = "grant_type=" + URLEncoder.encode( "client_credentials", StandardCharsets.UTF_8 )
                        + "&scope=" + URLEncoder.encode( "SALESFORCE_COMMERCE_API:" + instanceId, StandardCharsets.UTF_8 );

        // Build HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                        .uri( URI.create( tokenEndpoint ) )
                        .header( "Authorization", authHeader )
                        .header( "Content-Type", "application/x-www-form-urlencoded" )
                        .POST( HttpRequest.BodyPublishers.ofString( formData ) )
                        .build();

        try
        {
            // Send POST request to the OAuth service
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );

            // Extract and return the access token from the response
            if ( response.statusCode() >= 200 && response.statusCode() < 300 )
            {
                Map<String, String> responseBody = objectMapper.readValue( response.body(),
                                new TypeReference<Map<String, String>>()
                                {
                                } );

                Map<String, String> tokenAndExpiresIn = new HashMap<>();
                tokenAndExpiresIn.put( "access_token", responseBody.get( "access_token" ) );
                tokenAndExpiresIn.put( "expires_in", responseBody.get( "expires_in" ) );
                return tokenAndExpiresIn;
            }
            else if ( response.statusCode() == 401 )
            {
                throw new SQLException( "401 Unauthorized. Please verify your username and password." );
            }
            else if ( response.statusCode() == 400 )
            {
                throw new SQLException( "400 Bad Request: " + response.body() );
            }
        }
        catch ( IOException | InterruptedException e )
        {
            throw new SQLException( "Failed to retrieve OAuth token: " + e.getMessage(), e );
        }
        return null;
    }
}
