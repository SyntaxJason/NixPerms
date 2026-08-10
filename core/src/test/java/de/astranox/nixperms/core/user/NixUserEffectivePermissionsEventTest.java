package de.astranox.nixperms.core.user;

import de.astranox.nixperms.api.event.user.UserEffectivePermissionsChangeEvent;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.ResolutionPolicy;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import de.astranox.nixperms.core.config.DatabaseSettings;
import de.astranox.nixperms.core.config.NetworkSettings;
import de.astranox.nixperms.core.config.NixPermsSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NixUserEffectivePermissionsEventTest {

    @TempDir Path temporaryDirectory;

    @Test
    void publishesOnlyWhenAUsersEffectivePermissionsActuallyChange() throws Exception {
        try (NixPermsCore core = NixPermsCore.create(settings()).get(5, TimeUnit.SECONDS)) {
            IPermissionGroup owner = core.groups()
                    .create("owner", GroupRole.PRIMARY)
                    .get(5, TimeUnit.SECONDS);
            INixUser user = core.users()
                    .loadUser(UUID.randomUUID())
                    .get(5, TimeUnit.SECONDS);
            user.setPrimary(owner).get(5, TimeUnit.SECONDS);

            AtomicInteger changes = new AtomicInteger();
            AtomicReference<UserEffectivePermissionsChangeEvent> last = new AtomicReference<>();
            core.events().subscribe(UserEffectivePermissionsChangeEvent.class, event -> {
                changes.incrementAndGet();
                last.set(event);
            });

            core.groups().setPermission(owner, "*", true).get(5, TimeUnit.SECONDS);

            assertEquals(1, changes.get());
            assertNotNull(last.get());
            assertEquals(user.uniqueId(), last.get().user().uniqueId());
            assertTrue(last.get().current().has("nixperms.admin"));
            assertTrue(user.hasPermission("nixperms.admin"));

            core.groups().setWeight(owner, 100).get(5, TimeUnit.SECONDS);

            assertEquals(1, changes.get());
        }
    }

    private NixPermsSettings settings() {
        return new NixPermsSettings(
                DatabaseSettings.sqlite(temporaryDirectory.resolve("permissions.db")),
                new NetworkSettings(
                        false, "paper-test", Duration.ofSeconds(1), Duration.ofMinutes(5)
                ),
                "default", ResolutionPolicy.PRIMARY_WINS, 2
        );
    }
}
