package de.astranox.nixperms.core;

import de.astranox.nixperms.api.event.network.NetworkSyncEvent;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.ResolutionPolicy;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.config.DatabaseSettings;
import de.astranox.nixperms.core.config.NetworkSettings;
import de.astranox.nixperms.core.config.NixPermsSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NixPermsCoreNetworkIntegrationTest {

    @TempDir Path temporaryDirectory;

    @Test
    void synchronizesCommittedGroupAndLoadedUserUpdatesBetweenTwoCores() throws Exception {
        Path database = temporaryDirectory.resolve("network.db");
        NixPermsCore alpha = null;
        NixPermsCore beta = null;
        try {
            alpha = NixPermsCore.create(settings(database, "alpha")).get(5, TimeUnit.SECONDS);
            beta = NixPermsCore.create(settings(database, "beta")).get(5, TimeUnit.SECONDS);

            CountDownLatch groupApplied = new CountDownLatch(1);
            beta.events().subscribe(NetworkSyncEvent.class, event -> {
                if (event.syncType() == NetworkSyncEvent.SyncType.GROUP_UPDATE &&
                        "networked".equals(event.affectedGroupName())) {
                    groupApplied.countDown();
                }
            });

            alpha.groups().create("networked", GroupRole.PRIMARY, editor ->
                    editor.allow("network.command")
            ).get(5, TimeUnit.SECONDS);

            assertTrue(groupApplied.await(3, TimeUnit.SECONDS));
            assertNotNull(beta.groups().group("networked"));
            assertEquals(
                    PermissionDecision.ALLOW,
                    beta.groups().group("networked").permissions().globalDecision("network.command")
            );

            UUID uniqueId = UUID.randomUUID();
            beta.users().loadUser(uniqueId).get(5, TimeUnit.SECONDS);
            INixUser alphaUser = alpha.users().loadUser(uniqueId).get(5, TimeUnit.SECONDS);
            CountDownLatch userApplied = new CountDownLatch(1);
            beta.events().subscribe(NetworkSyncEvent.class, event -> {
                if (event.syncType() == NetworkSyncEvent.SyncType.USER_UPDATE &&
                        uniqueId.equals(event.affectedUserId())) {
                    userApplied.countDown();
                }
            });

            alphaUser.edit(editor -> editor.allow("network.user"))
                    .get(5, TimeUnit.SECONDS);

            assertTrue(userApplied.await(3, TimeUnit.SECONDS));
            assertTrue(beta.users().getUser(uniqueId).hasPermission("network.user"));
        } finally {
            if (beta != null) beta.close();
            if (alpha != null) alpha.close();
        }
    }

    private NixPermsSettings settings(Path database, String serverId) {
        return new NixPermsSettings(
                DatabaseSettings.sqlite(database),
                new NetworkSettings(
                        true, serverId, Duration.ofMillis(25), Duration.ofMinutes(2)
                ),
                "default", ResolutionPolicy.PRIMARY_WINS, 2
        );
    }
}
