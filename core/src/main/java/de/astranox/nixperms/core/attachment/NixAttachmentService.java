package de.astranox.nixperms.core.attachment;

import de.astranox.nixperms.api.attachment.IAttachmentService;
import de.astranox.nixperms.api.attachment.IUserAttachments;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.user.NixUser;
import de.astranox.nixperms.core.user.NixUserManager;

import java.util.Optional;
import java.util.UUID;

public final class NixAttachmentService implements IAttachmentService {

    private final NixUserManager users;

    public NixAttachmentService(NixUserManager users) {
        this.users = users;
    }

    @Override
    public Optional<IUserAttachments> forUser(UUID subjectId) {
        NixUser user = users.internalUser(subjectId);
        if (user == null) return Optional.empty();
        return Optional.of(user.attachments());
    }

    @Override
    public boolean invalidateAll(UUID subjectId) {
        NixUser user = users.internalUser(subjectId);
        if (user == null || user.attachments().all().isEmpty()) return false;
        user.attachments().clear();
        return true;
    }

    @Override
    public int invalidateNamespace(String namespace) {
        int removed = 0;
        for (INixUser user : users.loaded()) {
            removed += user.attachments().removeNamespace(namespace);
        }
        return removed;
    }
}
