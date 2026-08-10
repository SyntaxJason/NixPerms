package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.event.group.GroupCreateEvent;
import de.astranox.nixperms.api.event.group.GroupDeleteEvent;
import de.astranox.nixperms.api.event.group.GroupUpdateEvent;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IGroupEditor;
import de.astranox.nixperms.api.group.IGroupManager;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.core.model.GroupModel;
import de.astranox.nixperms.core.model.MetaEntryModel;
import de.astranox.nixperms.core.storage.IGroupStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class NixGroupManager implements IGroupManager {

    private final IGroupStorage storage;
    private final IEventBus eventBus;
    private final Executor executor;
    private final String defaultGroupName;
    private final ConcurrentHashMap<String, NixGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GroupModel> models = new ConcurrentHashMap<>();
    private final NixGroupChainCache chains = new NixGroupChainCache();
    private final ReentrantLock mutationLock = new ReentrantLock();
    private volatile Consumer<String> changeListener = ignored -> { };
    private volatile IPermissionGroup defaultGroup;

    public NixGroupManager(
            IGroupStorage storage,
            IEventBus eventBus,
            Executor executor,
            String defaultGroupName
    ) {
        this.storage = storage;
        this.eventBus = eventBus;
        this.executor = executor;
        this.defaultGroupName = GroupModel.normalizeName(defaultGroupName);
    }

    @Override
    public CompletableFuture<Void> loadAll() {
        return CompletableFuture.runAsync(this::loadAllBlocking, executor);
    }

    public CompletableFuture<Void> reloadGroup(String name) {
        String normalized = GroupModel.normalizeName(name);
        return CompletableFuture.runAsync(() -> withMutationLock(() -> {
            GroupModel model = storage.load(normalized);
            if (model == null) {
                removeCached(normalized);
            } else {
                new NixGroupValidator(models).validate(model);
                apply(model);
            }
            changeListener.accept(normalized);
            return null;
        }), executor);
    }

    public List<IPermissionGroup> getChain(IPermissionGroup root) {
        return chains.get(current(root));
    }

    public void onGroupChanged(Consumer<String> listener) {
        this.changeListener = listener == null ? ignored -> { } : listener;
    }

    @Override public IPermissionGroup defaultGroup() { return defaultGroup; }
    @Override public @Nullable IPermissionGroup group(String name) { return groups.get(normalizeOrEmpty(name)); }
    @Override public Collection<IPermissionGroup> loaded() { return List.copyOf(groups.values()); }

    @Override
    public CompletableFuture<IPermissionGroup> create(String name, GroupRole role) {
        return create(name, role, editor -> { });
    }

    @Override
    public CompletableFuture<IPermissionGroup> create(
            String name,
            GroupRole role,
            Consumer<IGroupEditor> initialState
    ) {
        String normalized = GroupModel.normalizeName(name);
        return CompletableFuture.supplyAsync(() -> withMutationLock(() -> {
            if (models.containsKey(normalized)) throw new IllegalArgumentException("Group already exists: " + normalized);
            GroupModel empty = GroupModel.empty(normalized, role);
            NixGroupEditor editor = new NixGroupEditor(empty);
            initialState.accept(editor);
            GroupModel created = editor.build();
            new NixGroupValidator(models).validate(created);
            storage.save(created, true);
            NixGroup group = apply(created);
            eventBus.post(new GroupCreateEvent(group, EventCause.API));
            changeListener.accept(normalized);
            return group;
        }), executor).thenApply(group -> (IPermissionGroup) group);
    }

    @Override
    public CompletableFuture<IPermissionGroup> edit(String name, Consumer<IGroupEditor> change) {
        String normalized = GroupModel.normalizeName(name);
        return CompletableFuture.supplyAsync(() -> withMutationLock(() -> {
            GroupModel current = models.get(normalized);
            if (current == null) throw new IllegalArgumentException("Unknown group: " + normalized);
            NixGroup previous = groups.get(normalized);
            NixGroupEditor editor = new NixGroupEditor(current);
            change.accept(editor);
            GroupModel updated = editor.build();
            new NixGroupValidator(models).validate(updated);
            storage.save(updated, true);
            NixGroup result = apply(updated);
            eventBus.post(new GroupUpdateEvent(previous, result, EventCause.API));
            changeListener.accept(normalized);
            return result;
        }), executor).thenApply(group -> (IPermissionGroup) group);
    }

    @Override
    public CompletableFuture<Void> delete(IPermissionGroup group) {
        String name = GroupModel.normalizeName(group.name());
        return CompletableFuture.runAsync(() -> withMutationLock(() -> {
            new NixGroupValidator(models).validateDelete(name, defaultGroupName);
            storage.delete(name, true);
            removeCached(name);
            eventBus.post(new GroupDeleteEvent(name, EventCause.API));
            changeListener.accept(name);
            return null;
        }), executor);
    }

    private void loadAllBlocking() {
        mutationLock.lock();
        try {
            Collection<GroupModel> loaded = storage.loadAll();
            Map<String, GroupModel> replacement = new LinkedHashMap<>();
            loaded.forEach(model -> replacement.put(model.name(), model));
            GroupModel fallback = replacement.get(defaultGroupName);
            if (fallback == null) {
                fallback = GroupModel.empty(defaultGroupName, GroupRole.PRIMARY);
                storage.save(fallback, false);
                replacement.put(defaultGroupName, fallback);
            }
            if (fallback.role() != GroupRole.PRIMARY) {
                throw new IllegalStateException("Default group must have role PRIMARY: " + defaultGroupName);
            }

            NixGroupValidator validator = new NixGroupValidator(replacement);
            replacement.values().forEach(validator::validate);

            models.clear();
            groups.clear();
            chains.clear();
            replacement.values().forEach(this::apply);
            defaultGroup = groups.get(defaultGroupName);
        } finally {
            mutationLock.unlock();
        }
    }

    private NixGroup apply(GroupModel model) {
        models.put(model.name(), model);
        NixGroup group = fromModel(model);
        groups.put(model.name(), group);
        chains.invalidate(model.name());
        if (model.name().equals(defaultGroupName)) defaultGroup = group;
        return group;
    }

    private void removeCached(String name) {
        models.remove(name);
        groups.remove(name);
        chains.invalidate(name);
    }

    private NixGroup fromModel(GroupModel model) {
        List<NixMetaEntry> prefixes = meta(model.prefixes());
        List<NixMetaEntry> suffixes = meta(model.suffixes());
        return new NixGroup(
                model.name(), model.role(), model.weight(), new NixGroupPermissionData(model.rules()),
                new NixGroupMeta(model.options(), prefixes, suffixes), model.parentName(),
                model.defaultSecondaryName(), groups::get, change -> edit(model.name(), change)
        );
    }

    private List<NixMetaEntry> meta(List<MetaEntryModel> entries) {
        return entries.stream()
                .map(entry -> new NixMetaEntry(entry.priority(), entry.value()))
                .sorted(Comparator.comparingInt(NixMetaEntry::priority).reversed())
                .toList();
    }

    private IPermissionGroup current(IPermissionGroup group) {
        IPermissionGroup current = groups.get(group.name());
        return current == null ? group : current;
    }

    private String normalizeOrEmpty(String name) {
        try { return GroupModel.normalizeName(name); }
        catch (IllegalArgumentException ignored) { return ""; }
    }

    private <T> T withMutationLock(java.util.function.Supplier<T> action) {
        mutationLock.lock();
        try { return action.get(); }
        finally { mutationLock.unlock(); }
    }
}
