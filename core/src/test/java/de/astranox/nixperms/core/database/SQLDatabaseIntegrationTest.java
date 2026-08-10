package de.astranox.nixperms.core.database;

import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.PermissionScope;
import de.astranox.nixperms.core.config.DatabaseSettings;
import de.astranox.nixperms.core.config.NetworkSettings;
import de.astranox.nixperms.core.model.GroupModel;
import de.astranox.nixperms.core.model.UserModel;
import de.astranox.nixperms.core.sync.SyncMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLDatabaseIntegrationTest {

    @TempDir Path temporaryDirectory;

    @Test
    void persistsScopedDataAndPublishesTransactionalOutbox() {
        try (SQLDatabase database = database(temporaryDirectory.resolve("network.db"))) {
            GroupModel group = group(List.of(new PermissionRule(
                    "chat.send", PermissionDecision.ALLOW, PermissionScope.server("hub")
            )));
            database.saveGroup(group, true);

            UUID uniqueId = UUID.randomUUID();
            UserModel user = new UserModel(
                    uniqueId, "TestPlayer", "default", null,
                    List.of(PermissionRule.deny("chat.spy"))
            );
            database.saveUser(user, true);

            assertEquals(group.rules(), database.getGroup("default").rules());
            assertEquals(uniqueId, database.getUserByName("testplayer").uniqueId());
            List<SyncMessage> updates = database.pollSync(0L);
            assertEquals(List.of("GROUP_UPDATE", "USER_UPDATE"), updates.stream().map(SyncMessage::type).toList());
        }
    }

    @Test
    void failedWriteRollsBackDataAndSyncMessageTogether() {
        try (SQLDatabase database = database(temporaryDirectory.resolve("rollback.db"))) {
            database.saveGroup(group(List.of(PermissionRule.allow("safe.node"))), true);
            long syncId = database.latestSyncId();

            GroupModel invalid = group(List.of(
                    PermissionRule.allow("duplicate.node"),
                    PermissionRule.deny("duplicate.node")
            ));
            assertThrows(StorageException.class, () -> database.saveGroup(invalid, true));

            assertEquals(List.of(PermissionRule.allow("safe.node")), database.getGroup("default").rules());
            assertEquals(syncId, database.latestSyncId());
        }
    }

    @Test
    void migratesLegacyGlobalPermissionTables() throws Exception {
        Path file = temporaryDirectory.resolve("legacy.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE nixperms_groups (" +
                    "name VARCHAR(64) PRIMARY KEY,role VARCHAR(16),weight INT,parent_name VARCHAR(64))");
            statement.execute("CREATE TABLE nixperms_users (" +
                    "uuid CHAR(36) PRIMARY KEY,primary_group VARCHAR(64),secondary_group VARCHAR(64))");
            statement.execute("CREATE TABLE nixperms_group_permissions (" +
                    "group_name VARCHAR(64),node VARCHAR(256),value INTEGER)");
            statement.execute("CREATE TABLE nixperms_user_permissions (" +
                    "uuid CHAR(36),node VARCHAR(256),value INTEGER)");
            statement.execute("INSERT INTO nixperms_groups VALUES ('default','PRIMARY',0,NULL)");
            statement.execute("INSERT INTO nixperms_group_permissions VALUES ('default','legacy.node',1)");
        }

        try (SQLDatabase database = database(file)) {
            assertTrue(database.getGroup("default").rules().contains(PermissionRule.allow("legacy.node")));
        }
    }

    private SQLDatabase database(Path file) {
        DatabaseSettings storage = DatabaseSettings.sqlite(file);
        NetworkSettings network = new NetworkSettings(
                true, "test-server", Duration.ofMillis(50), Duration.ofMinutes(5)
        );
        return new SQLDatabase(HikariPoolFactory.create(storage), SqlDialect.SQLITE, network);
    }

    private GroupModel group(List<PermissionRule> rules) {
        return new GroupModel(
                "default", GroupRole.PRIMARY, 0, null, null,
                rules, List.of(), List.of(), Map.of()
        );
    }
}
