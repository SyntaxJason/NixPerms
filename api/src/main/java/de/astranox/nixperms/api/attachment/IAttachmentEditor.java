package de.astranox.nixperms.api.attachment;

import de.astranox.nixperms.api.permission.IPermissionEditor;
import de.astranox.nixperms.api.permission.PermissionScope;

/** Fluent, atomic attachment editor. */
public interface IAttachmentEditor extends IPermissionEditor {
    @Override default IAttachmentEditor allow(String node) { return allow(node, PermissionScope.GLOBAL); }
    @Override IAttachmentEditor allow(String node, PermissionScope scope);
    @Override default IAttachmentEditor deny(String node) { return deny(node, PermissionScope.GLOBAL); }
    @Override IAttachmentEditor deny(String node, PermissionScope scope);
    @Override default IAttachmentEditor unset(String node) { return unset(node, PermissionScope.GLOBAL); }
    @Override IAttachmentEditor unset(String node, PermissionScope scope);
    @Override IAttachmentEditor clearPermissions();
}
