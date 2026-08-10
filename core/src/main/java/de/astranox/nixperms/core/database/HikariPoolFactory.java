package de.astranox.nixperms.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.astranox.nixperms.core.config.DatabaseSettings;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

public final class HikariPoolFactory {

    private HikariPoolFactory() { }

    public static HikariDataSource create(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("nixperms-storage");
        config.setMaximumPoolSize(settings.dialect() == SqlDialect.SQLITE ? 1 : settings.poolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);
        config.setAutoCommit(true);

        switch (settings.dialect()) {
            case SQLITE -> configureSqlite(config, settings.sqliteFile());
            case POSTGRESQL -> configureRemote(config, settings, "org.postgresql.Driver", "postgresql");
            case MYSQL -> configureRemote(config, settings, "com.mysql.cj.jdbc.Driver", "mysql");
            case MARIADB -> configureRemote(config, settings, "org.mariadb.jdbc.Driver", "mariadb");
        }
        return new HikariDataSource(config);
    }

    private static void configureSqlite(HikariConfig config, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        try {
            if (normalized.getParent() != null) Files.createDirectories(normalized.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the SQLite directory", exception);
        }
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + normalized);
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
    }

    private static void configureRemote(
            HikariConfig config,
            DatabaseSettings settings,
            String driver,
            String scheme
    ) {
        config.setDriverClassName(driver);
        config.setJdbcUrl("jdbc:" + scheme + "://" + settings.host() + ':' + settings.port() + '/' + settings.database());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.addDataSourceProperty("tcpKeepAlive", "true");
        if (settings.dialect() == SqlDialect.MYSQL || settings.dialect() == SqlDialect.MARIADB) {
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
    }
}
