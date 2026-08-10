package de.astranox.nixperms.paper;

import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import de.astranox.nixperms.core.permission.NixPermissionData;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissibleBase;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bridges NixPerms snapshots with Bukkit defaults and runtime PermissionAttachments. */
public final class BukkitPermissibleBase extends PermissibleBase {

    private final NixPermsCore core;
    private final UUID uniqueId;
    private volatile NixPermissionData runtimeAttachments;

    public BukkitPermissibleBase(Player player, NixPermsCore core) {
        super(player);
        this.core = core;
        this.uniqueId = player.getUniqueId();
        this.runtimeAttachments = new NixPermissionData(PermissionContext.GLOBAL, Map.of());
        recalculatePermissions();
    }

    @Override
    public boolean hasPermission(String name) {
        String node = PermissionRule.normalizeNode(name);
        if (node.isEmpty()) return false;

        PermissionDecision runtime = runtimeDecision(node);
        if (runtime.isSet()) return runtime.allowed();

        INixUser user = user();
        if (user != null) {
            PermissionDecision persisted = user.permissions().decision(node);
            if (persisted.isSet()) return persisted.allowed();
        }
        return super.hasPermission(node);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return permission != null && hasPermission(permission.getName());
    }

    @Override
    public boolean isPermissionSet(String name) {
        String node = PermissionRule.normalizeNode(name);
        if (node.isEmpty()) return false;
        if (runtimeDecision(node).isSet()) return true;
        INixUser user = user();
        if (user != null && user.permissions().decision(node).isSet()) return true;
        return super.isPermissionSet(node);
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return permission != null && isPermissionSet(permission.getName());
    }

    @Override
    public void recalculatePermissions() {
        super.recalculatePermissions();
        // PermissibleBase invokes this method from its constructor.
        if (uniqueId == null) return;

        Map<String, PermissionDecision> effective = new LinkedHashMap<>();
        for (PermissionAttachmentInfo info : super.getEffectivePermissions()) {
            if (info.getAttachment() == null) continue;
            String node = info.getPermission().toLowerCase(Locale.ROOT);
            effective.put(node, PermissionDecision.of(super.hasPermission(node)));
        }
        INixUser user = user();
        PermissionContext context = user == null ? PermissionContext.GLOBAL : user.context();
        runtimeAttachments = new NixPermissionData(context, effective);
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        INixUser user = user();
        if (user == null) return super.getEffectivePermissions();

        Map<String, PermissionAttachmentInfo> effective = new LinkedHashMap<>();
        Set<PermissionAttachmentInfo> bukkit = super.getEffectivePermissions();
        for (PermissionAttachmentInfo info : bukkit) {
            if (info.getAttachment() != null) continue;
            effective.put(info.getPermission().toLowerCase(Locale.ROOT), info);
        }
        user.permissions().effective().forEach((node, decision) -> effective.put(
                node, new PermissionAttachmentInfo(this, node, null, decision.allowed())
        ));
        for (PermissionAttachmentInfo info : bukkit) {
            if (info.getAttachment() == null) continue;
            effective.put(info.getPermission().toLowerCase(Locale.ROOT), info);
        }
        return Set.copyOf(effective.values());
    }

    private PermissionDecision runtimeDecision(String node) {
        NixPermissionData current = runtimeAttachments;
        return current == null ? PermissionDecision.UNSET : current.decision(node);
    }

    private INixUser user() {
        if (core == null || uniqueId == null) return null;
        return core.users().getUser(uniqueId);
    }
}
