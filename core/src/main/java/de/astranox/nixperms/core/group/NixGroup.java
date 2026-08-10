package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IGroupEditor;
import de.astranox.nixperms.api.group.IGroupMeta;
import de.astranox.nixperms.api.group.IGroupPermissionData;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.PermissionRule;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public final class NixGroup implements IPermissionGroup {

    private final String name;
    private final GroupRole role;
    private final int weight;
    private final NixGroupPermissionData permissions;
    private final NixGroupMeta meta;
    @Nullable private final String parentName;
    @Nullable private final String defaultSecondaryName;
    private final Function<String, IPermissionGroup> groupLookup;
    private final Function<Consumer<IGroupEditor>, CompletableFuture<IPermissionGroup>> editor;

    public NixGroup(
            String name,
            GroupRole role,
            int weight,
            NixGroupPermissionData permissions,
            NixGroupMeta meta,
            @Nullable String parentName,
            @Nullable String defaultSecondaryName,
            Function<String, IPermissionGroup> groupLookup,
            Function<Consumer<IGroupEditor>, CompletableFuture<IPermissionGroup>> editor
    ) {
        this.name = name;
        this.role = role;
        this.weight = weight;
        this.permissions = permissions;
        this.meta = meta;
        this.parentName = parentName;
        this.defaultSecondaryName = defaultSecondaryName;
        this.groupLookup = groupLookup;
        this.editor = editor;
    }

    @Override public String name() { return name; }
    @Override public GroupRole role() { return role; }
    @Override public int weight() { return weight; }
    @Override public IGroupPermissionData permissions() { return permissions; }
    @Override public Collection<PermissionRule> rules() { return permissions.rules(); }
    @Override public IGroupMeta meta() { return meta; }
    @Override public Optional<IPermissionGroup> parent() { return lookup(parentName); }
    @Override public Optional<IPermissionGroup> defaultSecondary() { return lookup(defaultSecondaryName); }
    @Override public CompletableFuture<IPermissionGroup> edit(Consumer<IGroupEditor> change) { return editor.apply(change); }

    private Optional<IPermissionGroup> lookup(@Nullable String groupName) {
        if (groupName == null) return Optional.empty();
        return Optional.ofNullable(groupLookup.apply(groupName));
    }
}
