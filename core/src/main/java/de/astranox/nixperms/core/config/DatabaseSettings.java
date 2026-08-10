package de.astranox.nixperms.core.config;

import de.astranox.nixperms.core.database.SqlDialect;

import java.nio.file.Path;

public record DatabaseSettings(
        SqlDialect dialect,
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize,
        Path sqliteFile
) {

    public DatabaseSettings {
        if (dialect == null) throw new IllegalArgumentException("Database dialect cannot be null");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid database port: " + port);
        if (poolSize < 1 || poolSize > 64) throw new IllegalArgumentException("Database pool size must be between 1 and 64");
        host = valueOrDefault(host, "127.0.0.1");
        database = valueOrDefault(database, "nixperms");
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        if (dialect == SqlDialect.SQLITE && sqliteFile == null) {
            throw new IllegalArgumentException("SQLite file cannot be null");
        }
    }

    public static DatabaseSettings sqlite(Path file) {
        return new DatabaseSettings(SqlDialect.SQLITE, "", 1, "nixperms", "", "", 1, file);
    }

    private static String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }
}
