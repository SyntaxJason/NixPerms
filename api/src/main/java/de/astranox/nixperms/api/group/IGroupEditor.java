package de.astranox.nixperms.api.group;

import de.astranox.nixperms.api.permission.IPermissionEditor;
import de.astranox.nixperms.api.permission.PermissionScope;

/** Atomic editor for one group. */
public interface IGroupEditor extends IPermissionEditor {
    IGroupEditor parent(String groupName);
    IGroupEditor clearParent();
    IGroupEditor defaultSecondary(String groupName);
    IGroupEditor clearDefaultSecondary();
    IGroupEditor weight(int weight);
    IGroupEditor option(String key, String value);
    IGroupEditor removeOption(String key);
    IGroupEditor prefix(int priority, String value);
    IGroupEditor removePrefix(int priority, String value);
    IGroupEditor suffix(int priority, String value);
    IGroupEditor removeSuffix(int priority, String value);

    @Override default IGroupEditor allow(String node) { return allow(node, PermissionScope.GLOBAL); }
    @Override IGroupEditor allow(String node, PermissionScope scope);
    @Override default IGroupEditor deny(String node) { return deny(node, PermissionScope.GLOBAL); }
    @Override IGroupEditor deny(String node, PermissionScope scope);
    @Override default IGroupEditor unset(String node) { return unset(node, PermissionScope.GLOBAL); }
    @Override IGroupEditor unset(String node, PermissionScope scope);
    @Override IGroupEditor clearPermissions();
}
