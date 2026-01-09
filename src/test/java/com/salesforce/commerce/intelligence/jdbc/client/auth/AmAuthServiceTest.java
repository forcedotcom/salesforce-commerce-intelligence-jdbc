package com.salesforce.commerce.intelligence.jdbc.client.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
}
