package de.astranox.nixperms.api.group;

import de.astranox.nixperms.api.permission.PermissionRule;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Immutable group snapshot with an atomic editing entry point. */
public interface IPermissionGroup {
    String name();
    int weight();
    GroupRole role();
    IGroupPermissionData permissions();
    Collection<PermissionRule> rules();
    IGroupMeta meta();
    Optional<IPermissionGroup> parent();
    Optional<IPermissionGroup> defaultSecondary();
    CompletableFuture<IPermissionGroup> edit(Consumer<IGroupEditor> editor);
}
