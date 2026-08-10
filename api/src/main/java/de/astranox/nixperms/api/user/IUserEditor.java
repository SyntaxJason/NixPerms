package de.astranox.nixperms.api.user;

import de.astranox.nixperms.api.permission.IPermissionEditor;
import de.astranox.nixperms.api.permission.PermissionScope;

/** Atomic persistent user editor. */
public interface IUserEditor extends IPermissionEditor {
    IUserEditor name(String name);
    IUserEditor primary(String groupName);
    IUserEditor secondary(String groupName);
    IUserEditor clearSecondary();

    @Override default IUserEditor allow(String node) { return allow(node, PermissionScope.GLOBAL); }
    @Override IUserEditor allow(String node, PermissionScope scope);
    @Override default IUserEditor deny(String node) { return deny(node, PermissionScope.GLOBAL); }
    @Override IUserEditor deny(String node, PermissionScope scope);
    @Override default IUserEditor unset(String node) { return unset(node, PermissionScope.GLOBAL); }
    @Override IUserEditor unset(String node, PermissionScope scope);
    @Override IUserEditor clearPermissions();
}
