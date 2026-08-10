package de.astranox.nixperms.platform.common;

import de.astranox.nixperms.core.config.NixPermsSettings;
import de.astranox.nixperms.core.database.SqlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSettingsLoaderTest {

    @TempDir
    Path dataDirectory;

    @Test
    void createsAndReadsSharedConfiguration() throws Exception {
        NixPermsSettings settings = load("""
                server-id: Lobby-01
                permissions:
                  default-group: Member
                  resolution-policy: deny_wins
                database:
                  type: sqlite
                  sqlite-file: permissions.db
                  threads: 3
                network:
                  enabled: false
                  poll-interval-ms: 500
                  retention-seconds: 60
                """);

        assertEquals("lobby-01", settings.network().serverId());
        assertEquals("member", settings.defaultGroup());
        assertEquals(SqlDialect.SQLITE, settings.database().dialect());
        assertEquals(dataDirectory.resolve("permissions.db"), settings.database().sqliteFile());
        assertEquals(3, settings.databaseThreads());
        assertTrue(Files.isRegularFile(dataDirectory.resolve("config.yml")));
    }

    @Test
    void rejectsNetworkPollingWithLocalSqlite() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> load("""
                        server-id: node-1
                        database:
                          type: sqlite
                        network:
                          enabled: true
                        """)
        );

        assertTrue(error.getMessage().contains("shared MySQL, MariaDB or PostgreSQL"));
    }

    @Test
    void rejectsDuplicateYamlKeys() {
        assertThrows(IllegalArgumentException.class, () -> load("""
                server-id: first
                server-id: second
                """));
    }

    private NixPermsSettings load(String yaml) {
        return PlatformSettingsLoader.load(dataDirectory, () -> new ByteArrayInputStream(
                yaml.getBytes(StandardCharsets.UTF_8)
        ));
    }
}
