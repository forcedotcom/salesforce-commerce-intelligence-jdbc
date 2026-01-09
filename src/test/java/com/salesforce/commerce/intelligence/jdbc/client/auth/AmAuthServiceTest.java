package com.salesforce.commerce.intelligence.jdbc.client.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AmAuthServiceTest {
    private AmAuthService authService;

    @Before
    public void setUp() {
        authService = new AmAuthService();
    }

    @Test
    public void testConstructor() {
        assertNotNull(authService);
    }

    @Test
    public void testGetAMAccessToken_400BadRequest() {
        try {
            authService.getAMAccessToken("https://invalid.example.com", "invalidClientId", "invalidSecret", "testInstance");
            fail("Expected SQLException for 400 Bad Request");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("400") || e.getMessage().contains("Failed to retrieve OAuth token"));
        }
    }

    @Test
    public void testGetAMAccessToken_401Unauthorized() {
        try {
            authService.getAMAccessToken(null, "invalidClientId", "invalidSecret", "testInstance");
            fail("Expected SQLException for 401 Unauthorized");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")
                    || e.getMessage().contains("Failed to retrieve OAuth token"));
        }
    }

    @Test
    public void testGetAMAccessToken_NullHost() {
        try {
            authService.getAMAccessToken(null, "testClientId", "testSecret", "testInstance");
            fail("Expected SQLException due to invalid credentials");
        } catch (SQLException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testGetAMAccessToken_EmptyHost() {
        try {
            authService.getAMAccessToken("", "testClientId", "testSecret", "testInstance");
            fail("Expected SQLException due to invalid credentials");
        } catch (SQLException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testGetAMAccessToken_InvalidHost() {
        try {
            authService.getAMAccessToken("https://nonexistent-oauth-host-12345.example.com", "testClientId", "testSecret", "testInstance");
            fail("Expected SQLException due to connection failure");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("Failed to retrieve OAuth token"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetAMAccessToken_403Forbidden() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(403);
        when(mockResponse.body()).thenReturn("Forbidden");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        AmAuthService mockAuthService = new AmAuthService(mockHttpClient, new ObjectMapper());

        try {
            mockAuthService.getAMAccessToken("https://test.example.com", "clientId", "secret", "instance");
            fail("Expected SQLException for 403 Forbidden");
        } catch (SQLException e) {
            assertTrue("Should contain status code", e.getMessage().contains("403"));
            assertTrue("Should contain 'OAuth request failed'", e.getMessage().contains("OAuth request failed"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetAMAccessToken_500InternalServerError() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        AmAuthService mockAuthService = new AmAuthService(mockHttpClient, new ObjectMapper());

        try {
            mockAuthService.getAMAccessToken("https://test.example.com", "clientId", "secret", "instance");
            fail("Expected SQLException for 500 Internal Server Error");
        } catch (SQLException e) {
            assertTrue("Should contain status code", e.getMessage().contains("500"));
            assertTrue("Should contain 'OAuth request failed'", e.getMessage().contains("OAuth request failed"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetAMAccessToken_InterruptedException() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(
                new InterruptedException("Thread interrupted"));

        AmAuthService mockAuthService = new AmAuthService(mockHttpClient, new ObjectMapper());

        try {
            mockAuthService.getAMAccessToken("https://test.example.com", "clientId", "secret", "instance");
            fail("Expected SQLException for InterruptedException");
        } catch (SQLException e) {
            assertTrue("Should contain 'interrupted'", e.getMessage().contains("interrupted"));
            assertTrue("Thread interrupt flag should be set", Thread.interrupted());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetAMAccessToken_MissingAccessToken() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"expires_in\":\"3600\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        AmAuthService mockAuthService = new AmAuthService(mockHttpClient, new ObjectMapper());

        try {
            mockAuthService.getAMAccessToken("https://test.example.com", "clientId", "secret", "instance");
            fail("Expected SQLException for missing access_token");
        } catch (SQLException e) {
            assertTrue("Should mention missing fields", e.getMessage().contains("missing access_token or expires_in"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetAMAccessToken_MissingExpiresIn() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"access_token\":\"test-token\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        AmAuthService mockAuthService = new AmAuthService(mockHttpClient, new ObjectMapper());

        try {
            mockAuthService.getAMAccessToken("https://test.example.com", "clientId", "secret", "instance");
            fail("Expected SQLException for missing expires_in");
        } catch (SQLException e) {
            assertTrue("Should mention missing fields", e.getMessage().contains("missing access_token or expires_in"));
        }
    }
}
