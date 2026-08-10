package de.astranox.nixperms.core.user;

import de.astranox.nixperms.api.attachment.IPermissionAttachment;
import de.astranox.nixperms.api.attachment.IUserAttachments;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.IPermissionData;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.PermissionScope;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.api.user.IUserEditor;
import de.astranox.nixperms.api.user.IUserSnapshot;
import de.astranox.nixperms.core.attachment.NixUserAttachments;
import de.astranox.nixperms.core.group.NixGroupManager;
import de.astranox.nixperms.core.model.UserModel;
import de.astranox.nixperms.core.permission.NixPermissionData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public final class NixUser implements INixUser {

    private final NixGroupManager groups;
    private final Function<Consumer<IUserEditor>, CompletableFuture<INixUser>> editor;
    private final Consumer<NixUser> refreshCallback;
    private final NixUserAttachments attachments;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean refreshRunning = new AtomicBoolean();
    private final AtomicBoolean refreshRequested = new AtomicBoolean();
    private volatile UserState state;

    NixUser(
            UserModel model,
            NixGroupManager groups,
            PermissionContext context,
            Function<Consumer<IUserEditor>, CompletableFuture<INixUser>> editor,
            Consumer<NixUser> refreshCallback
    ) {
        this.groups = groups;
        this.editor = editor;
        this.refreshCallback = refreshCallback;
        this.state = new UserState(
                model,
                context,
                new NixPermissionData(context, Map.of()),
                new NixMetaData("", "", Map.of()),
                0L
        );
        this.attachments = new NixUserAttachments(model.uniqueId(), this::requestRefresh);
    }

    @Override public UUID uniqueId() { return state.model().uniqueId(); }
    @Override public @Nullable String name() { return state.model().name(); }
    @Override public IPermissionGroup primary() { return resolvePrimary(state.model().primaryGroupName()); }
    @Override public @Nullable IPermissionGroup secondaryExplicit() { return resolveSecondary(state.model().secondaryGroupName()); }

    @Override
    public @Nullable IPermissionGroup secondaryEffective() {
        UserModel model = state.model();
        return secondaryEffective(model);
    }

    @Override public Collection<PermissionRule> ownRules() { return state.model().rules(); }

    @Override
    public Map<String, Boolean> ownPermissions() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (PermissionRule rule : state.model().rules()) {
            if (!PermissionScope.GLOBAL.equals(rule.scope())) continue;
            result.put(rule.node(), rule.decision().allowed());
        }
        return Map.copyOf(result);
    }

    @Override public IPermissionData permissions() { return state.permissions(); }
    @Override public IUserAttachments attachments() { return attachments; }
    @Override public PermissionContext context() { return state.context(); }
    @Override public boolean hasPermission(String node) { return state.permissions().decision(node) == PermissionDecision.ALLOW; }
    @Override public CompletableFuture<INixUser> edit(Consumer<IUserEditor> change) { return editor.apply(change); }

    @Override
    public void updateContext(PermissionContext newContext) {
        if (newContext == null) throw new IllegalArgumentException("Permission context cannot be null");

        synchronized (this) {
            UserState current = state;
            if (current.context().equals(newContext)) return;
            state = new UserState(
                    current.model(), newContext, current.permissions(), current.meta(), current.revision() + 1L
            );
        }
        enqueueRefresh();
    }

    @Override
    public IUserSnapshot snapshot() {
        UserState current = state;
        UserModel model = current.model();
        IPermissionGroup secondary = secondaryEffective(model);
        return new NixUserSnapshot(
                model.uniqueId(), model.name(), resolvePrimary(model.primaryGroupName()).name(),
                model.secondaryGroupName(), secondary == null ? null : secondary.name(),
                current.permissions(), current.meta()
        );
    }

    UserModel model() { return state.model(); }
    NixUserAttachments attachmentsRaw() { return attachments; }
    boolean active() { return active.get(); }
    long revision() { return state.revision(); }

    synchronized void apply(UserModel updated) {
        UserState current = state;
        state = new UserState(
                updated, current.context(), current.permissions(), current.meta(), current.revision() + 1L
        );
    }

    RefreshInput refreshInput() {
        UserState current = state;
        return new RefreshInput(
                current.revision(), current.model(), current.context(), attachments.snapshot()
        );
    }

    synchronized @Nullable IPermissionData publish(
            long expectedRevision,
            NixPermissionData permissions,
            NixMetaData meta
    ) {
        UserState current = state;
        if (current.revision() != expectedRevision) return null;
        state = new UserState(
                current.model(), current.context(), permissions, meta, current.revision()
        );
        return current.permissions();
    }

    void deactivate() {
        active.set(false);
        attachments.clear();
    }

    void invalidate() {
        bumpRevision();
        enqueueRefresh();
    }

    synchronized void bumpRevision() {
        UserState current = state;
        state = new UserState(
                current.model(), current.context(), current.permissions(), current.meta(), current.revision() + 1L
        );
    }

    boolean requestRefreshWorker() {
        refreshRequested.set(true);
        return refreshRunning.compareAndSet(false, true);
    }

    boolean consumeRefreshRequest() {
        return refreshRequested.getAndSet(false);
    }

    void releaseRefreshWorker() {
        refreshRunning.set(false);
    }

    boolean refreshPending() {
        return refreshRequested.get();
    }

    private @Nullable IPermissionGroup secondaryEffective(UserModel model) {
        IPermissionGroup explicit = resolveSecondary(model.secondaryGroupName());
        if (explicit != null) return explicit;
        return resolvePrimary(model.primaryGroupName())
                .defaultSecondary()
                .filter(group -> group.role() == GroupRole.SECONDARY)
                .orElse(null);
    }

    private IPermissionGroup resolvePrimary(String name) {
        IPermissionGroup group = groups.group(name);
        if (group != null && group.role() == GroupRole.PRIMARY) return group;
        return groups.defaultGroup();
    }

    private @Nullable IPermissionGroup resolveSecondary(@Nullable String name) {
        if (name == null) return null;
        IPermissionGroup group = groups.group(name);
        if (group == null || group.role() != GroupRole.SECONDARY) return null;
        return group;
    }

    private void requestRefresh() {
        synchronized (this) {
            UserState current = state;
            state = new UserState(
                    current.model(), current.context(), current.permissions(), current.meta(), current.revision() + 1L
            );
        }
        enqueueRefresh();
    }

    private void enqueueRefresh() {
        if (active()) refreshCallback.accept(this);
    }

    record RefreshInput(
            long revision,
            UserModel model,
            PermissionContext context,
            Collection<IPermissionAttachment> attachments
    ) { }

    private record UserState(
            UserModel model,
            PermissionContext context,
            NixPermissionData permissions,
            NixMetaData meta,
            long revision
    ) { }
}
