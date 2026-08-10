package de.astranox.nixperms.core.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaInitializer {

    private SchemaInitializer() { }

    public static void initialize(Connection connection, SqlDialect dialect) throws SQLException {
        createTables(connection, dialect);
        ensureLegacyColumns(connection);
        ensureExpandedValueColumns(connection, dialect);
        migrateLegacyRules(connection, dialect);
        ensureIndexes(connection);
    }

    private static void createTables(Connection connection, SqlDialect dialect) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_groups (" +
                    "name VARCHAR(64) NOT NULL PRIMARY KEY," +
                    "role VARCHAR(16) NOT NULL," +
                    "weight INT NOT NULL DEFAULT 0," +
                    "parent_name VARCHAR(64) NULL," +
                    "default_secondary VARCHAR(64) NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_users (" +
                    "uuid CHAR(36) NOT NULL PRIMARY KEY," +
                    "name VARCHAR(64) NULL," +
                    "name_lower VARCHAR(64) NULL," +
                    "primary_group VARCHAR(64) NOT NULL DEFAULT 'default'," +
                    "secondary_group VARCHAR(64) NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_group_rules (" +
                    "group_name VARCHAR(64) NOT NULL," +
                    "node VARCHAR(256) NOT NULL," +
                    "value " + dialect.boolType() + " NOT NULL," +
                    "server_scope VARCHAR(64) NOT NULL DEFAULT ''," +
                    "world_scope VARCHAR(64) NOT NULL DEFAULT ''," +
                    "PRIMARY KEY(group_name,node,server_scope,world_scope))");
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_user_rules (" +
                    "uuid CHAR(36) NOT NULL," +
                    "node VARCHAR(256) NOT NULL," +
                    "value " + dialect.boolType() + " NOT NULL," +
                    "server_scope VARCHAR(64) NOT NULL DEFAULT ''," +
                    "world_scope VARCHAR(64) NOT NULL DEFAULT ''," +
                    "PRIMARY KEY(uuid,node,server_scope,world_scope))");
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_group_meta (" +
                    "group_name VARCHAR(64) NOT NULL," +
                    "type VARCHAR(8) NOT NULL," +
                    "priority INT NOT NULL," +
                    "value VARCHAR(512) NOT NULL," +
                    "PRIMARY KEY(group_name,type,priority,value))");
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_group_options (" +
                    "group_name VARCHAR(64) NOT NULL," +
                    "key_name VARCHAR(64) NOT NULL," +
                    "value VARCHAR(512) NOT NULL," +
                    "PRIMARY KEY(group_name,key_name))");
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_sync (" +
                    "id " + dialect.syncIdDefinition() + ',' +
                    "server_id VARCHAR(64) NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "payload VARCHAR(256) NULL," +
                    "created_at BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS nixperms_schema_meta (" +
                    "key_name VARCHAR(64) NOT NULL PRIMARY KEY," +
                    "value VARCHAR(64) NOT NULL)");
        }
    }

    private static void ensureLegacyColumns(Connection connection) throws SQLException {
        ensureColumn(connection, "nixperms_groups", "default_secondary", "VARCHAR(64) NULL");
        ensureColumn(connection, "nixperms_users", "name", "VARCHAR(64) NULL");
        ensureColumn(connection, "nixperms_users", "name_lower", "VARCHAR(64) NULL");
    }

    private static void ensureExpandedValueColumns(Connection connection, SqlDialect dialect)
            throws SQLException {
        if (dialect == SqlDialect.SQLITE) return;
        ensureVarcharCapacity(connection, dialect, "nixperms_group_meta", "value", 512);
        ensureVarcharCapacity(connection, dialect, "nixperms_group_options", "value", 512);
    }

    private static void migrateLegacyRules(Connection connection, SqlDialect dialect) throws SQLException {
        if (migrationApplied(connection, "legacy-rules-v1")) return;
        if (tableExists(connection, "nixperms_group_permissions")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertIgnorePrefix(dialect) + " INTO nixperms_group_rules " +
                        "(group_name,node,value,server_scope,world_scope) " +
                        "SELECT legacy.group_name,legacy.node,legacy.value,'','' " +
                        "FROM nixperms_group_permissions legacy WHERE NOT EXISTS (" +
                        "SELECT 1 FROM nixperms_group_rules current " +
                        "WHERE current.group_name=legacy.group_name AND current.node=legacy.node " +
                        "AND current.server_scope='' AND current.world_scope='')" + conflictIgnoreSuffix(dialect));
            }
        }
        if (tableExists(connection, "nixperms_user_permissions")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(insertIgnorePrefix(dialect) + " INTO nixperms_user_rules " +
                        "(uuid,node,value,server_scope,world_scope) " +
                        "SELECT legacy.uuid,legacy.node,legacy.value,'','' " +
                        "FROM nixperms_user_permissions legacy WHERE NOT EXISTS (" +
                        "SELECT 1 FROM nixperms_user_rules current " +
                        "WHERE current.uuid=legacy.uuid AND current.node=legacy.node " +
                        "AND current.server_scope='' AND current.world_scope='')" + conflictIgnoreSuffix(dialect));
            }
        }
        String markerSql = switch (dialect) {
            case MYSQL, MARIADB -> "INSERT INTO nixperms_schema_meta (key_name,value) VALUES(?,?) " +
                    "ON DUPLICATE KEY UPDATE value=VALUES(value)";
            case POSTGRESQL, SQLITE -> "INSERT INTO nixperms_schema_meta (key_name,value) VALUES(?,?) " +
                    "ON CONFLICT(key_name) DO UPDATE SET value=excluded.value";
        };
        try (PreparedStatement statement = connection.prepareStatement(markerSql)) {
            statement.setString(1, "legacy-rules-v1");
            statement.setString(2, "done");
            statement.executeUpdate();
        }
    }

    private static String insertIgnorePrefix(SqlDialect dialect) {
        return switch (dialect) {
            case MYSQL, MARIADB -> "INSERT IGNORE";
            case SQLITE -> "INSERT OR IGNORE";
            case POSTGRESQL -> "INSERT";
        };
    }

    private static String conflictIgnoreSuffix(SqlDialect dialect) {
        return dialect == SqlDialect.POSTGRESQL ? " ON CONFLICT DO NOTHING" : "";
    }

    private static void ensureIndexes(Connection connection) throws SQLException {
        ensureIndex(connection, "nixperms_users", "idx_nixperms_users_name", "name_lower");
        ensureIndex(connection, "nixperms_sync", "idx_nixperms_sync_created", "created_at");
    }

    private static void ensureColumn(
            Connection connection,
            String table,
            String column,
            String definition
    ) throws SQLException {
        if (columnExists(connection, table, column)) return;
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + ' ' + definition);
            } catch (SQLException exception) {
                if (!columnExists(connection, table, column)) throw exception;
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, "%", "%")) {
            while (columns.next()) {
                if (!table.equalsIgnoreCase(columns.getString("TABLE_NAME"))) continue;
                if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
        }
        return false;
    }

    private static void ensureVarcharCapacity(
            Connection connection,
            SqlDialect dialect,
            String table,
            String column,
            int capacity
    ) throws SQLException {
        int current = columnCapacity(connection, table, column);
        if (current <= 0 || current >= capacity) return;
        String sql = switch (dialect) {
            case MYSQL, MARIADB -> "ALTER TABLE " + table + " MODIFY COLUMN " + column +
                    " VARCHAR(" + capacity + ") NOT NULL";
            case POSTGRESQL -> "ALTER TABLE " + table + " ALTER COLUMN " + column +
                    " TYPE VARCHAR(" + capacity + ')';
            case SQLITE -> throw new IllegalStateException("SQLite columns do not require expansion");
        };
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute(sql);
            } catch (SQLException exception) {
                if (columnCapacity(connection, table, column) < capacity) throw exception;
            }
        }
    }

    private static int columnCapacity(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, "%", "%")) {
            while (columns.next()) {
                if (!table.equalsIgnoreCase(columns.getString("TABLE_NAME"))) continue;
                if (!column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) continue;
                return columns.getInt("COLUMN_SIZE");
            }
        }
        return -1;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private static boolean migrationApplied(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM nixperms_schema_meta WHERE key_name=?"
        )) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void ensureIndex(
            Connection connection,
            String table,
            String index,
            String column
    ) throws SQLException {
        if (indexExists(connection, table, index)) return;
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("CREATE INDEX " + index + " ON " + table + '(' + column + ')');
            } catch (SQLException exception) {
                if (!indexExists(connection, table, index)) throw exception;
            }
        }
    }

    private static boolean indexExists(Connection connection, String table, String index) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, table, false, false
        )) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
            }
        }
        return false;
    }
}
