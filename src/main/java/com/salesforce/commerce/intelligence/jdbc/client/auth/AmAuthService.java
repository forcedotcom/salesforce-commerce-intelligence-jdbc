package com.salesforce.commerce.intelligence.jdbc.client.auth;

import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

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

    private RestTemplate restTemplate;

    /**
     * Constructs an AmAuthService instance and initializes the RestTemplate with JSON message converter.
     */
    public AmAuthService() {
        restTemplate = new RestTemplate();
        // Add the JSON message converter to handle JSON responses
        restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
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
                        .encodeToString( ( amClientId + ":" + amClientSecret ).getBytes() );

        // Set headers for the request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType( MediaType.APPLICATION_FORM_URLENCODED );
        headers.set( "Authorization", authHeader );

        // Set form parameters
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add( "grant_type", "client_credentials" );
        formData.add( "scope", "SALESFORCE_COMMERCE_API" + ":" + instanceId );

        // Build request entity
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>( formData, headers );

        try
        {
            // Send POST request to the OAuth service
            ResponseEntity<Map<String, String>> response =
                            restTemplate.exchange( tokenEndpoint, HttpMethod.POST, requestEntity,
                                            new ParameterizedTypeReference<Map<String, String>>()
                                            {
                                            } );

            // Extract and return the access token from the response
            if ( response.getStatusCode().is2xxSuccessful() )
            {
                Map tokenAndExpiresIn = new HashMap<>();
                tokenAndExpiresIn.put("access_token", response.getBody().get( "access_token" ));
                tokenAndExpiresIn.put( "expires_in",  response.getBody().get( "expires_in" ));
                return tokenAndExpiresIn;
            }
        }
        catch ( HttpClientErrorException e )
        {
            if ( e.getStatusCode().is4xxClientError() )
            {
                if ( e.getStatusCode() == HttpStatus.UNAUTHORIZED )
                {
                    throw new SQLException( "401 Unauthorized. Please verify your username and password." );
                }
                else if ( e.getStatusCode() == HttpStatus.BAD_REQUEST )
                {
                    throw new SQLException( "400 Bad Request." + e.getResponseBodyAsString() );
                }

            }
        } catch(Exception e)
        {
            throw new SQLException( e.getMessage() );
        }
        return null;
    }
}
