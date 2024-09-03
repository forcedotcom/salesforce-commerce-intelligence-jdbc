package org.cip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import org.apache.calcite.avatica.BuiltInConnectionProperty;
import org.apache.calcite.avatica.remote.Driver;
import org.cip.auth.AmAuthService;
import org.apache.calcite.avatica.ConnectionProperty;

/**
 * Custom JDBC driver that extends the Avatica remote driver specifically for handling connections to a Salesforce remote CIP database with
 * PostgreSQL dialect enabled.
 */
public class CIPDriver extends org.apache.calcite.avatica.remote.Driver {

    private static AmAuthService amAuthService;

    public CIPDriver() {
        amAuthService = new AmAuthService();
    }

    private static String CIP_JDBC_URL_PREFIX = "jdbc:salesforcecc:";

    // Static initializer to register this custom driver with the DriverManager.
    static {
        try {
            DriverManager.registerDriver(new CIPDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register the custom JDBC driver.", e);
        }
    }

    /**
     * Checks if the driver can accept the URL.
     *
     * @param url the URL of the database.
     * @return true if this driver can connect to the provided URL.
     */
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(getConnectStringPrefix());
    }

    /**
     * Provides the prefix for the JDBC connection string that this driver can handle.
     *
     * @return the connection string prefix specific to this driver.
     */
    @Override
    protected String getConnectStringPrefix() {
        return CIP_JDBC_URL_PREFIX;
    }

    /**
     * Attempts to establish a connection to the given database URL.
     *
     * @param url the database URL to connect to.
     * @param info a list of arbitrary string tag/value pairs as connection arguments.
     * @return a Connection object that represents a connection to the URL.
     * @throws SQLException if a database access error occurs or the url is invalid.
     */
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            throwCIPBadRequestForInvalidJDBCUrl();
        }

        // Modify the URL from PostgreSQL style to Avatica style
        ConnectionResult result = convertPostgresUrlToAvatica(url);

        // Here you can add logic to handle authentication or other security features.
        if (info != null) {
            // Example for potential authentication handling
            String clientId = info.getProperty("user");
            String clientIdSecret = info.getProperty("password");

            // AM host override
            String amOauthHost = info.getProperty("amOauthHost");

            String amToken = amAuthService.getAMAccessToken(amOauthHost, clientId, clientIdSecret, result.getDatabaseName());
            info.setProperty("jwtToken", amToken);
        }

        // Set PostgreSQL dialect for the connection
        info.setProperty("fun", "postgresql");

        String serialization = info.getProperty(BuiltInConnectionProperty.SERIALIZATION.camelName());

        if (serialization == null || serialization.isEmpty()) {
            info.setProperty(BuiltInConnectionProperty.SERIALIZATION.camelName(), Driver.Serialization.PROTOBUF.name());
        }

        info.setProperty("instanceId", result.getDatabaseName());

        // Pass the modified URL to the parent connect method
        return super.connect(result.getModifiedUrl(), info);
    }

    /**
     * Converts a PostgreSQL-style JDBC URL to an Avatica-compatible URL.
     *
     * @param url the PostgreSQL-style JDBC URL.
     * @return a ConnectionResult containing the converted Avatica URL and the extracted database name.
     */
    ConnectionResult convertPostgresUrlToAvatica(String url) throws SQLException {
        String protocolPrefix = CIP_JDBC_URL_PREFIX + "//";
        String avaticaPrefix = CIP_JDBC_URL_PREFIX + "url=";

        if (!url.startsWith(protocolPrefix)) {
            throw new IllegalArgumentException("Invalid PostgreSQL URL format");
        }

        return convertToAvaticaUrl(url, protocolPrefix, avaticaPrefix);
    }

    /**
     * Helper method to convert the URL from PostgreSQL to Avatica format.
     *
     * @param url the original URL.
     * @param protocolPrefix the prefix of the original URL.
     * @param avaticaPrefix the prefix for the Avatica URL.
     * @return a ConnectionResult containing the converted URL and database name.
     */
    private ConnectionResult convertToAvaticaUrl(String url, String protocolPrefix, String avaticaPrefix) throws SQLException {
        String remaining = url.substring(protocolPrefix.length());
        int pathIndex = remaining.indexOf('/');

        if (pathIndex == -1) {
            throwCIPBadRequestForInvalidJDBCUrl();
        }
        int queryIndex = remaining.indexOf('?');

        String hostnamePort = remaining.substring(0, pathIndex);

        if (hostnamePort == null || hostnamePort.isEmpty()) {
            throwCIPBadRequestForInvalidJDBCUrl();
        }

        String database = queryIndex == -1 ? remaining.substring(pathIndex + 1) : remaining.substring(pathIndex + 1, queryIndex);

        // This isn't currently used
        String parameters = queryIndex == -1 ? "" : remaining.substring(queryIndex);

        // Construct the Avatica URL
        String avaticaUrl = avaticaPrefix + "https://" + hostnamePort;
        return new ConnectionResult(avaticaUrl, database);
    }

    @Override
    public Collection<ConnectionProperty> getConnectionProperties() {
        return Collections.EMPTY_LIST;
    }

    /**
     * Inner class to encapsulate the result of converting a PostgreSQL URL to Avatica format, including the modified URL and the extracted
     * database name.
     */
    static class ConnectionResult {
        private final String modifiedUrl;
        private final String databaseName;

        public ConnectionResult(String modifiedUrl, String databaseName) {
            this.modifiedUrl = modifiedUrl;
            this.databaseName = databaseName;
        }

        public String getModifiedUrl() {
            return modifiedUrl;
        }

        public String getDatabaseName() {
            return databaseName;
        }
    }

    void throwCIPBadRequestForInvalidJDBCUrl() throws SQLException {
        throw new SQLException("Invalid Connection String: The connection string must start with '" + getConnectStringPrefix()
                + "' (e.g., '" + getConnectStringPrefix() + "//localhost:1234/testdb').");

    }
}
