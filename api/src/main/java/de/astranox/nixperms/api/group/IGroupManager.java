package de.astranox.nixperms.api.group;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IGroupManager {
    IPermissionGroup defaultGroup();
    @Nullable IPermissionGroup group(String name);
    Collection<IPermissionGroup> loaded();
    CompletableFuture<IPermissionGroup> create(String name, GroupRole role);
    CompletableFuture<IPermissionGroup> create(String name, GroupRole role, Consumer<IGroupEditor> initialState);
    CompletableFuture<IPermissionGroup> edit(String name, Consumer<IGroupEditor> editor);
    CompletableFuture<Void> delete(IPermissionGroup group);
    CompletableFuture<Void> loadAll();

    default CompletableFuture<IPermissionGroup> createPrimary(String name) {
        return create(name, GroupRole.PRIMARY);
    }

    default CompletableFuture<IPermissionGroup> createSecondary(String name) {
        return create(name, GroupRole.SECONDARY);
    }

    default CompletableFuture<Void> setParent(IPermissionGroup group, @Nullable IPermissionGroup parent) {
        return group.edit(editor -> {
            if (parent == null) editor.clearParent();
            if (parent != null) editor.parent(parent.name());
        }).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> setPermission(IPermissionGroup group, String node, boolean value) {
        return group.edit(editor -> {
            if (value) editor.allow(node);
            if (!value) editor.deny(node);
        }).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> unsetPermission(IPermissionGroup group, String node) {
        return group.edit(editor -> editor.unset(node)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> setOption(IPermissionGroup group, String key, String value) {
        return group.edit(editor -> editor.option(key, value)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> unsetOption(IPermissionGroup group, String key) {
        return group.edit(editor -> editor.removeOption(key)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> addPrefix(IPermissionGroup group, int priority, String value) {
        return group.edit(editor -> editor.prefix(priority, value)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> removePrefix(IPermissionGroup group, int priority, String value) {
        return group.edit(editor -> editor.removePrefix(priority, value)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> addSuffix(IPermissionGroup group, int priority, String value) {
        return group.edit(editor -> editor.suffix(priority, value)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> removeSuffix(IPermissionGroup group, int priority, String value) {
        return group.edit(editor -> editor.removeSuffix(priority, value)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> setWeight(IPermissionGroup group, int weight) {
        return group.edit(editor -> editor.weight(weight)).thenApply(ignored -> null);
    }
}
