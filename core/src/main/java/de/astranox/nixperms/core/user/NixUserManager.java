package de.astranox.nixperms.core.user;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.event.user.UserEffectivePermissionsChangeEvent;
import de.astranox.nixperms.api.event.user.UserLoadEvent;
import de.astranox.nixperms.api.event.user.UserUnloadEvent;
import de.astranox.nixperms.api.event.user.UserUpdateEvent;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.IPermissionData;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.ResolutionPolicy;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.api.user.IUserEditor;
import de.astranox.nixperms.api.user.IUserManager;
import de.astranox.nixperms.api.user.IUserSnapshot;
import de.astranox.nixperms.core.group.NixGroupManager;
import de.astranox.nixperms.core.model.UserModel;
import de.astranox.nixperms.core.permission.NixPermissionData;
import de.astranox.nixperms.core.permission.NixPermissionResolver;
import de.astranox.nixperms.core.profile.IProfileResolver;
import de.astranox.nixperms.core.profile.MojangProfileResolver;
import de.astranox.nixperms.core.profile.ResolvedProfile;
import de.astranox.nixperms.core.storage.IUserStorage;
import de.astranox.nixperms.core.util.StripedLock;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class NixUserManager implements IUserManager {

    private static final int MAX_REFRESH_RETRIES = 3;

    private final IUserStorage storage;
    private final NixGroupManager groups;
    private final NixPermissionResolver resolver;
    private final NixMetaResolver metaResolver;
    private final IEventBus eventBus;
    private final ResolutionPolicy policy;
    private final Executor storageExecutor;
    private final Executor computeExecutor;
    private final IProfileResolver profileResolver;
    private final PermissionContext initialContext;
    private final ConcurrentHashMap<UUID, NixUser> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<NixUser>> pendingLoads = new ConcurrentHashMap<>();
    private final StripedLock mutationLocks = new StripedLock(64);

    public NixUserManager(
            IUserStorage storage,
            NixGroupManager groups,
            NixPermissionResolver resolver,
            IEventBus eventBus,
            ResolutionPolicy policy,
            Executor storageExecutor,
            Executor computeExecutor,
            String serverId
    ) {
        this(
                storage, groups, resolver, eventBus, policy, storageExecutor, computeExecutor,
                serverId, new MojangProfileResolver()
        );
    }

    public NixUserManager(
            IUserStorage storage,
            NixGroupManager groups,
            NixPermissionResolver resolver,
            IEventBus eventBus,
            ResolutionPolicy policy,
            Executor storageExecutor,
            Executor computeExecutor,
            String serverId,
            IProfileResolver profileResolver
    ) {
        if (profileResolver == null) throw new IllegalArgumentException("Profile resolver cannot be null");
        this.storage = storage;
        this.groups = groups;
        this.resolver = resolver;
        this.metaResolver = new NixMetaResolver(groups);
        this.eventBus = eventBus;
        this.policy = policy;
        this.storageExecutor = storageExecutor;
        this.computeExecutor = computeExecutor;
        this.profileResolver = profileResolver;
        this.initialContext = PermissionContext.server(serverId);
    }

    @Override public @Nullable INixUser getUser(UUID uniqueId) { return users.get(uniqueId); }

    @Override
    public CompletableFuture<INixUser> loadUser(UUID uniqueId) {
        if (uniqueId == null) return CompletableFuture.failedFuture(new IllegalArgumentException("UUID cannot be null"));
        NixUser loaded = users.get(uniqueId);
        if (loaded != null) return CompletableFuture.completedFuture(loaded);
        return pendingLoads.computeIfAbsent(uniqueId, this::startLoad).thenApply(user -> user);
    }

    @Override
    public CompletableFuture<INixUser> reloadUser(UUID uniqueId) {
        NixUser loaded = users.get(uniqueId);
        if (loaded == null) return loadUser(uniqueId);

        return CompletableFuture.supplyAsync(() -> {
            UserModel model = storage.load(uniqueId);
            return model == null ? UserModel.create(uniqueId, groups.defaultGroup().name()) : model;
        }, storageExecutor).thenApplyAsync(model -> withLock(uniqueId, () -> {
            NixUser current = users.get(uniqueId);
            if (current == null) {
                NixUser created = buildUser(sanitize(model));
                NixUser existing = users.putIfAbsent(uniqueId, created);
                if (existing != null) {
                    created.deactivate();
                    return existing;
                }
                eventBus.post(new UserLoadEvent(created, EventCause.RELOAD));
                return created;
            }
            current.apply(sanitize(model));
            refreshUser(current);
            return current;
        }), computeExecutor);
    }

    @Override
    public CompletableFuture<INixUser> edit(UUID uniqueId, Consumer<IUserEditor> change) {
        if (change == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Editor cannot be null"));
        return loadUser(uniqueId).thenCompose(ignored -> CompletableFuture.supplyAsync(
                () -> mutate(uniqueId, change), storageExecutor
        ));
    }

    @Override
    public CompletableFuture<Void> saveUser(INixUser user) {
        if (!(user instanceof NixUser nixUser)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Foreign user implementation"));
        }
        return CompletableFuture.runAsync(() -> withLock(nixUser.uniqueId(), () -> {
            storage.save(nixUser.model(), true);
            return null;
        }), storageExecutor);
    }

    @Override
    public CompletableFuture<@Nullable IUserSnapshot> fetchSnapshot(UUID uniqueId) {
        NixUser loaded = users.get(uniqueId);
        if (loaded != null) return CompletableFuture.completedFuture(loaded.snapshot());

        return CompletableFuture.supplyAsync(() -> storage.load(uniqueId), storageExecutor)
                .thenApplyAsync(model -> {
                    if (model == null) return null;
                    NixUser temporary = buildUser(sanitize(model));
                    IUserSnapshot snapshot = temporary.snapshot();
                    temporary.deactivate();
                    return snapshot;
                }, computeExecutor);
    }

    @Override
    public CompletableFuture<@Nullable INixUser> resolveUser(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return CompletableFuture.completedFuture(null);
        String input = nameOrUuid.trim();
        try {
            return loadUser(UUID.fromString(input)).thenApply(user -> user);
        } catch (IllegalArgumentException ignored) {
            // Continue with name resolution.
        }

        for (NixUser user : users.values()) {
            if (user.name() != null && user.name().equalsIgnoreCase(input)) {
                return CompletableFuture.completedFuture(user);
            }
        }

        return CompletableFuture.supplyAsync(
                () -> storage.loadByName(input.toLowerCase(Locale.ROOT)), storageExecutor
        ).thenCompose(model -> {
            if (model != null) return loadUser(model.uniqueId()).thenApply(user -> user);
            return profileResolver.resolve(input).thenCompose(this::loadResolvedProfile);
        });
    }


    private CompletableFuture<@Nullable INixUser> loadResolvedProfile(@Nullable ResolvedProfile profile) {
        if (profile == null) return CompletableFuture.completedFuture(null);
        return loadUser(profile.uniqueId()).thenCompose(user -> {
            if (profile.name().equals(user.name())) return CompletableFuture.completedFuture(user);
            return edit(profile.uniqueId(), editor -> editor.name(profile.name()));
        });
    }

    @Override
    public void unloadUser(UUID uniqueId) {
        NixUser removed = users.remove(uniqueId);
        if (removed != null) {
            unload(removed);
            return;
        }
        CompletableFuture<NixUser> pending = pendingLoads.get(uniqueId);
        if (pending != null) {
            pending.whenComplete((user, error) -> {
                if (error == null) unloadLoaded(uniqueId);
            });
            return;
        }
        unloadLoaded(uniqueId);
    }

    private void unloadLoaded(UUID uniqueId) {
        NixUser removed = users.remove(uniqueId);
        if (removed != null) unload(removed);
    }

    private void unload(NixUser removed) {
        IUserSnapshot snapshot = removed.snapshot();
        removed.deactivate();
        eventBus.post(new UserUnloadEvent(snapshot, EventCause.API));
    }

    @Override public Collection<INixUser> loaded() { return List.copyOf(users.values()); }

    public @Nullable NixUser internalUser(UUID uniqueId) { return users.get(uniqueId); }

    public void invalidateGroup(String groupName) {
        CompletableFuture<?>[] refreshes = List.copyOf(users.values()).stream()
                .map(user -> CompletableFuture.runAsync(() -> withLock(user.uniqueId(), () -> {
                    NixUser current = users.get(user.uniqueId());
                    if (current == null || !usesGroup(current, groupName)) return null;
                    UserModel model = current.model();
                    UserModel sanitized = sanitize(model);
                    if (!sanitized.equals(model)) current.apply(sanitized);
                    current.bumpRevision();
                    refreshUser(current);
                    return null;
                }), computeExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(refreshes).join();
    }

    public void refreshAll() {
        users.values().forEach(NixUser::invalidate);
    }

    public CompletableFuture<Void> reloadAllLoaded() {
        CompletableFuture<?>[] reloads = users.keySet().stream()
                .map(this::reloadUser)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(reloads);
    }

    private CompletableFuture<NixUser> startLoad(UUID uniqueId) {
        CompletableFuture<NixUser> future = CompletableFuture.supplyAsync(() -> {
            UserModel model = storage.load(uniqueId);
            if (model != null) return model;
            UserModel created = UserModel.create(uniqueId, groups.defaultGroup().name());
            storage.save(created, true);
            return created;
        }, storageExecutor).thenApplyAsync(model -> {
            NixUser created = buildUser(sanitize(model));
            NixUser existing = users.putIfAbsent(uniqueId, created);
            if (existing != null) {
                created.deactivate();
                return existing;
            }
            eventBus.post(new UserLoadEvent(created, EventCause.API));
            return created;
        }, computeExecutor);
        future.whenComplete((result, error) -> pendingLoads.remove(uniqueId, future));
        return future;
    }

    private INixUser mutate(UUID uniqueId, Consumer<IUserEditor> change) {
        return withLock(uniqueId, () -> {
            NixUser user = users.get(uniqueId);
            if (user == null) throw new IllegalStateException("User was unloaded during edit: " + uniqueId);
            IUserSnapshot previous = user.snapshot();
            NixUserEditor editor = new NixUserEditor(user.model());
            change.accept(editor);
            UserModel updated = editor.build();
            new NixUserValidator(groups).validate(updated);
            storage.save(updated, true);
            user.apply(updated);
            refreshUser(user);
            eventBus.post(new UserUpdateEvent(previous, user.snapshot(), EventCause.API));
            return user;
        });
    }

    private NixUser buildUser(UserModel model) {
        NixUser user = new NixUser(
                model, groups, initialContext,
                change -> edit(model.uniqueId(), change), this::scheduleRefresh
        );
        refreshUser(user);
        return user;
    }

    private void scheduleRefresh(NixUser user) {
        if (!user.active() || !user.requestRefreshWorker()) return;
        computeExecutor.execute(() -> drainRefreshes(user));
    }

    private void drainRefreshes(NixUser user) {
        try {
            while (user.active() && user.consumeRefreshRequest()) {
                refreshUser(user);
            }
        } finally {
            user.releaseRefreshWorker();
            if (user.active() && user.refreshPending()) scheduleRefresh(user);
        }
    }

    private void refreshUser(NixUser user) {
        if (!user.active()) return;
        for (int attempt = 0; attempt < MAX_REFRESH_RETRIES; attempt++) {
            NixUser.RefreshInput input = user.refreshInput();
            IPermissionGroup primary = primary(input.model());
            IPermissionGroup secondary = secondary(input.model(), primary);
            NixPermissionData permissions = resolver.compute(
                    primary, secondary, input.model().rules(), input.attachments(),
                    input.context(), policy
            );
            NixMetaData meta = metaResolver.resolve(primary, secondary);
            IPermissionData previous = user.publish(input.revision(), permissions, meta);
            if (previous == null) continue;
            if (!previous.effective().equals(permissions.effective())) {
                eventBus.post(new UserEffectivePermissionsChangeEvent(user, previous, permissions));
            }
            return;
        }
    }

    private UserModel sanitize(UserModel model) {
        IPermissionGroup primary = groups.group(model.primaryGroupName());
        String secondaryName = model.secondaryGroupName();
        IPermissionGroup secondary = secondaryName == null ? null : groups.group(secondaryName);
        if (secondaryName != null && (secondary == null || secondary.role() != GroupRole.SECONDARY)) {
            secondaryName = null;
        }
        if (primary == null || primary.role() != GroupRole.PRIMARY) {
            return new UserModel(
                    model.uniqueId(), model.name(), groups.defaultGroup().name(),
                    secondaryName, model.rules()
            );
        }
        if (model.secondaryGroupName() != null && secondaryName == null) {
            return new UserModel(
                    model.uniqueId(), model.name(), model.primaryGroupName(), null, model.rules()
            );
        }
        return model;
    }

    private IPermissionGroup primary(UserModel model) {
        IPermissionGroup group = groups.group(model.primaryGroupName());
        if (group != null && group.role() == GroupRole.PRIMARY) return group;
        return groups.defaultGroup();
    }

    private @Nullable IPermissionGroup secondary(UserModel model, IPermissionGroup primary) {
        String secondaryName = model.secondaryGroupName();
        if (secondaryName != null) {
            IPermissionGroup explicit = groups.group(secondaryName);
            if (explicit != null && explicit.role() == GroupRole.SECONDARY) return explicit;
        }
        return primary.defaultSecondary().filter(group -> group.role() == GroupRole.SECONDARY).orElse(null);
    }

    private boolean usesGroup(NixUser user, String groupName) {
        UserModel model = user.model();
        if (model.primaryGroupName().equals(groupName)) return true;
        if (groupName.equals(model.secondaryGroupName())) return true;
        for (IPermissionGroup group : groups.getChain(primary(model))) {
            if (group.name().equals(groupName)) return true;
        }
        IPermissionGroup secondary = secondary(model, primary(model));
        if (secondary == null) return false;
        for (IPermissionGroup group : groups.getChain(secondary)) {
            if (group.name().equals(groupName)) return true;
        }
        return false;
    }

    private <T> T withLock(UUID uniqueId, java.util.function.Supplier<T> action) {
        ReentrantLock lock = mutationLocks.forKey(uniqueId);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
