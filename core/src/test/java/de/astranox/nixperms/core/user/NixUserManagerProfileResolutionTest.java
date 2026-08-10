package de.astranox.nixperms.core.user;

import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.event.INixEvent;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.permission.ResolutionPolicy;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.group.NixGroupManager;
import de.astranox.nixperms.core.model.GroupModel;
import de.astranox.nixperms.core.model.UserModel;
import de.astranox.nixperms.core.permission.NixPermissionResolver;
import de.astranox.nixperms.core.profile.ResolvedProfile;
import de.astranox.nixperms.core.storage.IGroupStorage;
import de.astranox.nixperms.core.storage.IUserStorage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NixUserManagerProfileResolutionTest {

    @Test
    void resolvesAndPersistsNeverJoinedProfileAfterLocalMiss() {
        Executor direct = Runnable::run;
        IEventBus events = noEvents();
        NixGroupManager groups = groups(events, direct);
        Map<UUID, UserModel> stored = new ConcurrentHashMap<>();
        IUserStorage storage = storage(stored);
        UUID expectedId = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        AtomicInteger lookups = new AtomicInteger();

        NixUserManager manager = new NixUserManager(
                storage,
                groups,
                new NixPermissionResolver(groups::getChain),
                events,
                ResolutionPolicy.PRIMARY_WINS,
                direct,
                direct,
                "test",
                name -> {
                    lookups.incrementAndGet();
                    return CompletableFuture.completedFuture(new ResolvedProfile(expectedId, "Notch"));
                }
        );

        INixUser resolved = manager.resolveUser("Notch").join();
        assertNotNull(resolved);
        assertEquals(expectedId, resolved.uniqueId());
        assertEquals("Notch", resolved.name());
        assertEquals("Notch", stored.get(expectedId).name());

        INixUser local = manager.resolveUser("notch").join();
        assertSame(resolved, local);
        assertEquals(1, lookups.get());
    }

    private NixGroupManager groups(IEventBus events, Executor executor) {
        GroupModel defaultGroup = GroupModel.empty("default", GroupRole.PRIMARY);
        IGroupStorage storage = new IGroupStorage() {
            @Override public Collection<GroupModel> loadAll() { return List.of(defaultGroup); }
            @Override public @Nullable GroupModel load(String name) {
                return "default".equals(name) ? defaultGroup : null;
            }
            @Override public void save(GroupModel model, boolean broadcast) { }
            @Override public void delete(String name, boolean broadcast) { }
        };
        NixGroupManager groups = new NixGroupManager(storage, events, executor, "default");
        groups.loadAll().join();
        return groups;
    }

    private IUserStorage storage(Map<UUID, UserModel> stored) {
        return new IUserStorage() {
            @Override public @Nullable UserModel load(UUID uniqueId) { return stored.get(uniqueId); }

            @Override
            public @Nullable UserModel loadByName(String name) {
                return stored.values().stream()
                        .filter(model -> model.name() != null && model.name().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null);
            }

            @Override public void save(UserModel model, boolean broadcast) {
                stored.put(model.uniqueId(), model);
            }
        };
    }

    private IEventBus noEvents() {
        return new IEventBus() {
            @Override public <T extends INixEvent> void subscribe(Class<T> eventType, Consumer<T> listener) { }
            @Override public <T extends INixEvent> void unsubscribe(Class<T> eventType, Consumer<T> listener) { }
            @Override public void post(INixEvent event) { }
        };
    }
}
