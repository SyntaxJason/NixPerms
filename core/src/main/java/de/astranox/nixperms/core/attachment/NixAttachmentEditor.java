package de.astranox.nixperms.core.attachment;

import de.astranox.nixperms.api.attachment.IAttachmentEditor;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.core.permission.NixPermissionEditor;

import java.util.Collection;

final class NixAttachmentEditor extends NixPermissionEditor<NixAttachmentEditor> implements IAttachmentEditor {

    NixAttachmentEditor(Collection<PermissionRule> initialRules) {
        super(initialRules);
    }
}
