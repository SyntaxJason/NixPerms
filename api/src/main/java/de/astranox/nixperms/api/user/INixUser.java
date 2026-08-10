package de.astranox.nixperms.api.user;

import de.astranox.nixperms.api.attachment.IUserAttachments;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.IPermissionData;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionRule;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface INixUser {
    UUID uniqueId();
    @Nullable String name();
    IPermissionGroup primary();
    @Nullable IPermissionGroup secondaryExplicit();
    @Nullable IPermissionGroup secondaryEffective();
    Collection<PermissionRule> ownRules();
    Map<String, Boolean> ownPermissions();
    IPermissionData permissions();
    IUserAttachments attachments();
    PermissionContext context();
    boolean hasPermission(String node);
    CompletableFuture<INixUser> edit(Consumer<IUserEditor> editor);
    void updateContext(PermissionContext context);
    IUserSnapshot snapshot();

    default CompletableFuture<Void> setPermission(String node, boolean value) {
        return edit(editor -> {
            if (value) editor.allow(node);
            if (!value) editor.deny(node);
        }).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> unsetPermission(String node) {
        return edit(editor -> editor.unset(node)).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> setPrimary(IPermissionGroup group) {
        return edit(editor -> editor.primary(group.name())).thenApply(ignored -> null);
    }

    default CompletableFuture<Void> setSecondary(@Nullable IPermissionGroup group) {
        return edit(editor -> {
            if (group == null) editor.clearSecondary();
            if (group != null) editor.secondary(group.name());
        }).thenApply(ignored -> null);
    }
}
