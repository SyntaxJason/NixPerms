package de.astranox.nixperms.api.permission;

/** Fluent editor shared by users, groups and attachments. */
public interface IPermissionEditor {

    default IPermissionEditor allow(String node) {
        return allow(node, PermissionScope.GLOBAL);
    }

    IPermissionEditor allow(String node, PermissionScope scope);

    default IPermissionEditor deny(String node) {
        return deny(node, PermissionScope.GLOBAL);
    }

    IPermissionEditor deny(String node, PermissionScope scope);

    default IPermissionEditor unset(String node) {
        return unset(node, PermissionScope.GLOBAL);
    }

    IPermissionEditor unset(String node, PermissionScope scope);

    IPermissionEditor clearPermissions();
}
