package org.cip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Custom JDBC driver that extends the Avatica remote driver specifically for handling connections to a Salesforce remote database with
 * PostgreSQL dialect enabled.
 */
public class Driver extends org.apache.calcite.avatica.remote.Driver {
    // Static initializer to register this custom driver with the DriverManager.
    static {
        try {
            DriverManager.registerDriver(new Driver());
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
        return url.startsWith(getConnectStringPrefix());
    }

    /**
     * Provides the prefix for the JDBC connection string that this driver can handle.
     *
     * @return the connection string prefix specific to this driver.
     */
    @Override
    protected String getConnectStringPrefix() {
        return "jdbc:salesforce:remote:";
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
            throw new SQLException("Invalid Connection String: The connection string must start with '" + getConnectStringPrefix()
                    + "' (e.g., '" + getConnectStringPrefix() + "//localhost:1234/testdb').");
        }

        // Set PostgreSQL dialect for the connection.
        info.setProperty("fun", "postgresql");

        // Here we can add logic to handle authentication management (JWT) or other security features.
        // Example:
        // String token = AuthenticationService.getToken();
        // info.setProperty("jwtToken", token);

        return super.connect(url, info);
    }
}
