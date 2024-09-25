package com.salesforce.commerce.intelligence.jdbc.client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import com.salesforce.commerce.intelligence.jdbc.client.util.LoggingManager;
import org.apache.calcite.avatica.BuiltInConnectionProperty;
import org.apache.calcite.avatica.ConnectionProperty;
import org.apache.calcite.avatica.remote.Driver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Custom JDBC driver that extends the Avatica remote driver specifically for handling connections to a Salesforce remote CIP database with
 * PostgreSQL dialect enabled.
 */
public class CIPDriver extends Driver {

    // ThreadLocal to store properties per thread
    public static ThreadLocal<Properties> connectionProperties = new ThreadLocal<>();

    private static final Logger LOG = LogManager.getLogger(CIPDriver.class);

    public CIPDriver() {
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
    public Connection connect(String url, Properties info) throws SQLException
    {

        // Control logging based on the 'enableLogging' property in the Properties object
        // Configure logging for the classes
        LoggingManager.configureLogging(info);

        LOG.debug( "In connect method" );

        connectionProperties.set( info );
        try
        {
            if ( !acceptsURL( url ) )
            {
                throwCIPBadRequestForInvalidJDBCUrl();
            }

            ConnectionResult result = null;

            if ( info != null )
            {
                // Check for SSL property; default to `true` for HTTPS if not provided
                boolean isSSL = true; // Default to HTTPS
                String sslProperty = info.getProperty( "ssl" );

                if ( sslProperty != null )
                {
                    if ( sslProperty.equalsIgnoreCase( "true" ) )
                    {
                        isSSL = true;
                    }
                    else if ( sslProperty.equalsIgnoreCase( "false" ) )
                    {
                        isSSL = false;
                    }
                    else
                    {
                        // Handle invalid value by throwing an exception
                        throw new SQLException(
                                        "Invalid value for ssl property. Expected 'true' or 'false', but got: " + sslProperty );
                    }
                }
                // Modify the URL from PostgreSQL style to Avatica style
                result = convertPostgresUrlToAvatica( url, isSSL );

                // Handle the case where result is null
                if ( result == null )
                {
                    throw new SQLException( "Failed to convert PostgreSQL URL to Avatica URL." );
                }

            }

            // Set PostgreSQL dialect for the connection
            info.setProperty( "fun", "postgresql" );

            String serialization = info.getProperty( BuiltInConnectionProperty.SERIALIZATION.camelName() );

            if ( serialization == null || serialization.isEmpty() )
            {
                info.setProperty( BuiltInConnectionProperty.SERIALIZATION.camelName(), Serialization.PROTOBUF.name() );
            }

            info.setProperty( "instanceId", result.getDatabaseName() );

            // Specify the use of a custom HTTP client implementation for Avatica.
            // This custom client will handle injecting the Authorization token into each request,
            // and it also manages token refreshing when the token is about to expire.
            //
            // The 'BuiltInConnectionProperty.HTTP_CLIENT_IMPL' property allows you to specify a
            // fully qualified class name of a custom AvaticaHttpClient implementation.
            //
            // In this case, we are using 'com.salesforce.commerce.intelligence.jdbc.client.CIPAvaticaHttpClient', which extends the default
            // AvaticaCommonsHttpClientImpl behavior to:
            //   - Add a JWT token to the Authorization header for every request.
            //   - Refresh the token automatically before it expires.
            //
            // This approach ensures secure communication between the JDBC client and the Avatica server.
            info.setProperty( BuiltInConnectionProperty.HTTP_CLIENT_IMPL.camelName(),
                            "com.salesforce.commerce.intelligence.jdbc.client.CIPAvaticaHttpClient" );

            // Call the overridable method instead of super.connect directly
            return doConnect( result.getModifiedUrl(), info );
        }
        finally
        {
            // Always ensure that the ThreadLocal is cleared
            connectionProperties.remove();
            LOG.debug( "ThreadLocal connectionProperties cleared after connection setup." );
        }
    }

    // This method is protected so that it can be overridden in tests
    // Pass the modified URL to the parent connect method
    protected Connection doConnect(String url, Properties info) throws SQLException {
        return super.connect(url, info);
    }

    /**
     * Converts a PostgreSQL-style JDBC URL to an Avatica-compatible URL.
     *
     * @param url the PostgreSQL-style JDBC URL.
     * @return a ConnectionResult containing the converted Avatica URL and the extracted database name.
     */
    ConnectionResult convertPostgresUrlToAvatica(String url, boolean isSSL) throws SQLException {
        String protocolPrefix = CIP_JDBC_URL_PREFIX + "//";
        String avaticaPrefix = CIP_JDBC_URL_PREFIX + "url=";

        if (!url.startsWith(protocolPrefix)) {
            throw new IllegalArgumentException("Invalid PostgreSQL URL format");
        }

        return convertToAvaticaUrl(url, protocolPrefix, avaticaPrefix, isSSL);
    }

    /**
     * Helper method to convert a PostgreSQL-style URL to an Avatica-compatible URL.
     *
     * This method takes a PostgreSQL JDBC URL, extracts the hostname and database name, and converts it into a format compatible with
     * Avatica. The method also toggles between HTTP and HTTPS based on the provided SSL setting.
     *
     * Example: Input URL: "jdbc:postgresql://localhost:5432/mydatabase?user=user&password=pass" Output URL:
     * "jdbc:avatica:url=https://localhost:5432"
     *
     * @param url the original PostgreSQL-style JDBC URL.
     * @param protocolPrefix the prefix of the original URL (e.g., "jdbc:postgresql://").
     * @param avaticaPrefix the prefix for the Avatica URL (e.g., "jdbc:avatica:url=").
     * @param isSSL boolean flag indicating whether to use HTTPS (true) or HTTP (false).
     * @return a ConnectionResult containing the converted URL and database name.
     * @throws SQLException if the URL format is invalid or other issues arise during conversion.
     */
    private ConnectionResult convertToAvaticaUrl(String url, String protocolPrefix, String avaticaPrefix, boolean isSSL)
            throws SQLException {
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
        String avaticaUrl = avaticaPrefix + (isSSL ? "https" : "http") + "://" + hostnamePort;
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
