package com.salesforce.commerce.intelligence.jdbc.client;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.avatica.AvaticaConnection;
import org.apache.calcite.avatica.AvaticaDatabaseMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatabaseMetaData that returns the primary keys, foreign keys and indexes of the warehouse tables.
 * <p>
 * The Avatica protocol has no request for these, so the Avatica client returns empty results without calling the server. Here they are read
 * with a query on the server's information schema tables ({@code information.primary_keys}, {@code foreign_keys}, {@code indexes}), which
 * the server builds from the PostgreSQL catalog using the JDBC table and column names. The result sets have the standard JDBC columns, so
 * DB tools show them under each table.
 * <p>
 * The tables are in the {@code warehouse} schema; any other schema returns an empty result. Update and delete rules are not read: the
 * warehouse foreign keys use the default (NO ACTION).
 * <p>
 * A server without these tables rejects the query (HTTP 400 while preparing it). Then this connection returns Avatica's empty results, as
 * before. Any other error is thrown.
 */
public class CIPDatabaseMetaData extends AvaticaDatabaseMetaData {

    private static final Logger LOG = LoggerFactory.getLogger(CIPDatabaseMetaData.class);

    static final String WAREHOUSE_SCHEMA = "warehouse";

    static final String PRIMARY_KEYS_SQL = "SELECT CAST(NULL AS VARCHAR) AS \"TABLE_CAT\", 'warehouse' AS \"TABLE_SCHEM\", "
            + "table_name AS \"TABLE_NAME\", column_name AS \"COLUMN_NAME\", CAST(key_seq AS SMALLINT) AS \"KEY_SEQ\", "
            + "pk_name AS \"PK_NAME\" FROM information.primary_keys";

    static final String FOREIGN_KEYS_SQL = "SELECT CAST(NULL AS VARCHAR) AS \"PKTABLE_CAT\", 'warehouse' AS \"PKTABLE_SCHEM\", "
            + "pk_table_name AS \"PKTABLE_NAME\", pk_column_name AS \"PKCOLUMN_NAME\", "
            + "CAST(NULL AS VARCHAR) AS \"FKTABLE_CAT\", 'warehouse' AS \"FKTABLE_SCHEM\", "
            + "fk_table_name AS \"FKTABLE_NAME\", fk_column_name AS \"FKCOLUMN_NAME\", CAST(key_seq AS SMALLINT) AS \"KEY_SEQ\", "
            + "CAST(" + importedKeyNoAction + " AS SMALLINT) AS \"UPDATE_RULE\", " + "CAST(" + importedKeyNoAction
            + " AS SMALLINT) AS \"DELETE_RULE\", " + "fk_name AS \"FK_NAME\", CAST(NULL AS VARCHAR) AS \"PK_NAME\", " + "CAST("
            + importedKeyNotDeferrable + " AS SMALLINT) AS \"DEFERRABILITY\" FROM information.foreign_keys";

    static final String INDEXES_SQL = "SELECT CAST(NULL AS VARCHAR) AS \"TABLE_CAT\", 'warehouse' AS \"TABLE_SCHEM\", "
            + "table_name AS \"TABLE_NAME\", non_unique AS \"NON_UNIQUE\", CAST(NULL AS VARCHAR) AS \"INDEX_QUALIFIER\", "
            + "index_name AS \"INDEX_NAME\", CAST(" + tableIndexOther + " AS SMALLINT) AS \"TYPE\", "
            + "CAST(ordinal_position AS SMALLINT) AS \"ORDINAL_POSITION\", column_name AS \"COLUMN_NAME\", "
            + "CAST(NULL AS VARCHAR) AS \"ASC_OR_DESC\", CAST(NULL AS BIGINT) AS \"CARDINALITY\", "
            + "CAST(NULL AS BIGINT) AS \"PAGES\", CAST(NULL AS VARCHAR) AS \"FILTER_CONDITION\" FROM information.indexes";

    /** The server's error when it cannot prepare a statement, e.g. because a table does not exist. */
    static final String PREPARE_ERROR = "Error while preparing statement";

    /** Set when the server has no key metadata tables, so later calls do not ask it again. */
    private volatile boolean keyTablesMissing;

    protected CIPDatabaseMetaData(AvaticaConnection connection) {
        super(connection);
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        if (!isWarehouse(schema)) {
            return super.getPrimaryKeys(catalog, schema, table);
        }
        return query(PRIMARY_KEYS_SQL, "table_name", table, null, "\"TABLE_NAME\", \"COLUMN_NAME\"",
                () -> super.getPrimaryKeys(catalog, schema, table));
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        if (!isWarehouse(schema)) {
            return super.getImportedKeys(catalog, schema, table);
        }
        return query(FOREIGN_KEYS_SQL, "fk_table_name", table, null, "\"PKTABLE_NAME\", \"FK_NAME\", \"KEY_SEQ\"",
                () -> super.getImportedKeys(catalog, schema, table));
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        if (!isWarehouse(schema)) {
            return super.getExportedKeys(catalog, schema, table);
        }
        return query(FOREIGN_KEYS_SQL, "pk_table_name", table, null, "\"FKTABLE_NAME\", \"FK_NAME\", \"KEY_SEQ\"",
                () -> super.getExportedKeys(catalog, schema, table));
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog,
            String foreignSchema, String foreignTable) throws SQLException {
        if (!isWarehouse(parentSchema) || !isWarehouse(foreignSchema)) {
            return super.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable);
        }
        List<String> conditions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        addCondition(conditions, values, "pk_table_name", parentTable);
        addCondition(conditions, values, "fk_table_name", foreignTable);
        return query(FOREIGN_KEYS_SQL, conditions, values, "\"FKTABLE_NAME\", \"FK_NAME\", \"KEY_SEQ\"",
                () -> super.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable));
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
        if (!isWarehouse(schema)) {
            return super.getIndexInfo(catalog, schema, table, unique, approximate);
        }
        return query(INDEXES_SQL, "table_name", table, unique ? "non_unique = FALSE" : null,
                "\"NON_UNIQUE\", \"INDEX_NAME\", \"ORDINAL_POSITION\"",
                () -> super.getIndexInfo(catalog, schema, table, unique, approximate));
    }

    /** A null schema means any schema; otherwise it has to be the warehouse schema. */
    static boolean isWarehouse(String schema) {
        return schema == null || WAREHOUSE_SCHEMA.equalsIgnoreCase(schema);
    }

    /** Avatica's own (empty) result, used when the server has no key metadata tables. */
    @FunctionalInterface
    interface Fallback {
        ResultSet get() throws SQLException;
    }

    private ResultSet query(String select, String tableColumn, String table, String extraCondition, String orderBy,
            Fallback fallback) throws SQLException {
        List<String> conditions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        addCondition(conditions, values, tableColumn, table);
        if (extraCondition != null) {
            conditions.add(extraCondition);
        }
        return query(select, conditions, values, orderBy, fallback);
    }

    private static void addCondition(List<String> conditions, List<String> values, String column, String value) {
        // A null table means all tables.
        if (value != null) {
            conditions.add(column + " = ?");
            values.add(value);
        }
    }

    private ResultSet query(String select, List<String> conditions, List<String> values, String orderBy, Fallback fallback)
            throws SQLException {
        if (keyTablesMissing) {
            return fallback.get();
        }
        String sql = select + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions)) + " ORDER BY " + orderBy;
        PreparedStatement statement;
        try {
            statement = getConnection().prepareStatement(sql);
        } catch (SQLException e) {
            if (!isMissingKeyTables(e)) {
                throw e;
            }
            // An older server has no information.primary_keys / foreign_keys / indexes and cannot prepare the query.
            LOG.warn("Server has no key metadata tables, returning empty keys and indexes for this connection: {}", e.getMessage());
            keyTablesMissing = true;
            return fallback.get();
        }
        try {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            ResultSet resultSet = statement.executeQuery();
            // The statement closes when the caller closes the result set.
            statement.closeOnCompletion();
            return resultSet;
        } catch (SQLException | RuntimeException e) {
            try {
                statement.close();
            } catch (SQLException | RuntimeException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    /**
     * Returns whether the server rejected the query as one it cannot prepare (HTTP 400), which for these fixed queries means the
     * information tables do not exist. Connection, authentication and server errors are not.
     */
    static boolean isMissingKeyTables(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause()) {
            String message = e.getMessage();
            if (message != null && message.contains("status code 400") && message.contains(PREPARE_ERROR)) {
                return true;
            }
        }
        return false;
    }
}
