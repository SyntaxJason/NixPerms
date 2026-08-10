package de.astranox.nixperms.platform.common;

import de.astranox.nixperms.api.permission.ResolutionPolicy;
import de.astranox.nixperms.core.config.DatabaseSettings;
import de.astranox.nixperms.core.config.NetworkSettings;
import de.astranox.nixperms.core.config.NixPermsSettings;
import de.astranox.nixperms.core.database.SqlDialect;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class PlatformSettingsLoader {

    private static final String CONFIG_FILE = "config.yml";

    private PlatformSettingsLoader() { }

    public static NixPermsSettings load(
            Path dataDirectory,
            Supplier<InputStream> defaultConfig
    ) {
        if (dataDirectory == null) throw new IllegalArgumentException("Data directory cannot be null");
        if (defaultConfig == null) throw new IllegalArgumentException("Default config supplier cannot be null");

        Path normalizedDirectory = dataDirectory.toAbsolutePath().normalize();
        Path configFile = normalizedDirectory.resolve(CONFIG_FILE);
        try {
            Files.createDirectories(normalizedDirectory);
            copyDefault(configFile, defaultConfig);
            return settings(normalizedDirectory, read(configFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load " + configFile, exception);
        } catch (YAMLException exception) {
            throw new IllegalArgumentException("Could not parse " + configFile, exception);
        }
    }

    private static void copyDefault(
            Path configFile,
            Supplier<InputStream> defaultConfig
    ) throws IOException {
        if (Files.exists(configFile)) return;
        try (InputStream input = defaultConfig.get()) {
            if (input == null) throw new IllegalStateException("Bundled config.yml is missing");
            Files.copy(input, configFile);
        }
    }

    private static Values read(Path configFile) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(1_000_000);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (InputStream input = Files.newInputStream(configFile)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("config.yml must contain a YAML object");
            }
            return new Values(map);
        }
    }

    private static NixPermsSettings settings(Path dataDirectory, Values values) {
        SqlDialect dialect = SqlDialect.from(values.string("database.type", "sqlite"));
        DatabaseSettings database = database(dataDirectory, values, dialect);
        NetworkSettings network = new NetworkSettings(
                values.bool("network.enabled", false),
                values.string("server-id", "node-1"),
                Duration.ofMillis(values.longValue("network.poll-interval-ms", 1_000L)),
                Duration.ofSeconds(values.longValue("network.retention-seconds", 300L))
        );
        if (network.enabled() && dialect == SqlDialect.SQLITE) {
            throw new IllegalArgumentException(
                    "Network polling requires a shared MySQL, MariaDB or PostgreSQL database"
            );
        }

        ResolutionPolicy policy = ResolutionPolicy.valueOf(
                values.string("permissions.resolution-policy", "PRIMARY_WINS")
                        .trim().toUpperCase(Locale.ROOT)
        );
        return new NixPermsSettings(
                database,
                network,
                values.string("permissions.default-group", "default"),
                policy,
                values.intValue("database.threads", 4)
        );
    }

    private static DatabaseSettings database(
            Path dataDirectory,
            Values values,
            SqlDialect dialect
    ) {
        String sqliteName = values.string("database.sqlite-file", "nixperms.db");
        Path sqliteFile = dataDirectory.resolve(sqliteName).normalize();
        int defaultPort = dialect == SqlDialect.POSTGRESQL ? 5432 : 3306;
        return new DatabaseSettings(
                dialect,
                values.string("database.host", "127.0.0.1"),
                values.intValue("database.port", defaultPort),
                values.string("database.name", "nixperms"),
                values.string("database.username", "nixperms"),
                values.string("database.password", ""),
                values.intValue("database.pool-size", 10),
                sqliteFile
        );
    }

    private record Values(Map<?, ?> root) {

        String string(String path, String fallback) {
            Object value = value(path);
            return value == null ? fallback : String.valueOf(value);
        }

        boolean bool(String path, boolean fallback) {
            Object value = value(path);
            if (value instanceof Boolean result) return result;
            if (value == null) return fallback;
            return Boolean.parseBoolean(String.valueOf(value));
        }

        int intValue(String path, int fallback) {
            long value = longValue(path, fallback);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(path + " is outside the integer range");
            }
            return (int) value;
        }

        long longValue(String path, long fallback) {
            Object value = value(path);
            if (value instanceof Number number) return number.longValue();
            if (value == null) return fallback;
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(path + " must be a number", exception);
            }
        }

        private Object value(String path) {
            Object current = root;
            for (String segment : path.split("\\.")) {
                if (!(current instanceof Map<?, ?> map)) return null;
                current = map.get(segment);
            }
            return current;
        }
    }
}
