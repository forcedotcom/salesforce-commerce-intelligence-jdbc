package com.salesforce.commerce.intelligence.jdbc.client;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.calcite.avatica.AvaticaConnection;
import org.apache.calcite.avatica.AvaticaFactory;
import org.apache.calcite.avatica.AvaticaPreparedStatement;
import org.apache.calcite.avatica.AvaticaResultSet;
import org.apache.calcite.avatica.AvaticaSpecificDatabaseMetaData;
import org.apache.calcite.avatica.AvaticaStatement;
import org.apache.calcite.avatica.Meta;
import org.apache.calcite.avatica.QueryState;
import org.apache.calcite.avatica.UnregisteredDriver;

/**
 * Avatica factory that returns {@link CIPDatabaseMetaData}, so key and index metadata comes from the server. Everything else is done by
 * Avatica's own factory (its JDBC 4.1 factory class is not public, so it is wrapped, not extended).
 */
public class CIPJdbcFactory implements AvaticaFactory {

    private final AvaticaFactory delegate;

    public CIPJdbcFactory(AvaticaFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getJdbcMajorVersion() {
        return delegate.getJdbcMajorVersion();
    }

    @Override
    public int getJdbcMinorVersion() {
        return delegate.getJdbcMinorVersion();
    }

    @Override
    public AvaticaConnection newConnection(UnregisteredDriver driver, AvaticaFactory factory, String url, Properties info)
            throws SQLException {
        // The connection keeps 'factory' (this one) and asks it for the DatabaseMetaData.
        return delegate.newConnection(driver, factory, url, info);
    }

    @Override
    public AvaticaStatement newStatement(AvaticaConnection connection, Meta.StatementHandle h, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        return delegate.newStatement(connection, h, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public AvaticaPreparedStatement newPreparedStatement(AvaticaConnection connection, Meta.StatementHandle h, Meta.Signature signature,
            int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return delegate.newPreparedStatement(connection, h, signature, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public AvaticaResultSet newResultSet(AvaticaStatement statement, QueryState state, Meta.Signature signature, TimeZone timeZone,
            Meta.Frame firstFrame) throws SQLException {
        return delegate.newResultSet(statement, state, signature, timeZone, firstFrame);
    }

    @Override
    public AvaticaSpecificDatabaseMetaData newDatabaseMetaData(AvaticaConnection connection) {
        return new CIPDatabaseMetaData(connection);
    }

    @Override
    public ResultSetMetaData newResultSetMetaData(AvaticaStatement statement, Meta.Signature signature) throws SQLException {
        return delegate.newResultSetMetaData(statement, signature);
    }
}
