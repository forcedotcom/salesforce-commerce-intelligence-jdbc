package org.cip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.apache.calcite.avatica.ConnectionConfigImpl;
import org.apache.calcite.avatica.ConnectionProperty;

/**
 * Custom JDBC driver that extends the Avatica remote driver specifically for handling connections to a Salesforce remote database with
 * PostgreSQL dialect enabled.
 */
public class CIPDriver extends org.apache.calcite.avatica.remote.Driver {

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

    @Override
    public Collection<ConnectionProperty> getConnectionProperties() {
        return Arrays.asList(createConnectionProperty("instanceId", ConnectionProperty.Type.STRING, true),
                createConnectionProperty("clientId", ConnectionProperty.Type.STRING, true),
                createConnectionProperty("clientSecret", ConnectionProperty.Type.STRING, true));
    }

    private ConnectionProperty createConnectionProperty(String name, ConnectionProperty.Type type, boolean required) {
        return new ConnectionProperty() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String camelName() {
                // Converts to camelCase if necessary, for now, it's the same as the name.
                return name;
            }

            @Override
            public Object defaultValue() {
                return null; // Default value is null, modify if needed
            }

            @Override
            public Type type() {
                return type;
            }

            @Override
            public ConnectionConfigImpl.PropEnv wrap(Properties properties) {
                // Implement this if specific wrapping is needed
                return null;
            }

            @Override
            public boolean required() {
                return required;
            }

            @Override
            public Class<?> valueClass() {
                return String.class; // Assuming all properties are of type String
            }
        };
    }
}
