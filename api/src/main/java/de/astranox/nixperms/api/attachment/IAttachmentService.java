package de.astranox.nixperms.api.attachment;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Entry point for attachments when only a user UUID is available. */
public interface IAttachmentService {
    Optional<IUserAttachments> forUser(UUID subjectId);

    default IPermissionAttachment create(
            UUID subjectId,
            String key,
            Consumer<IAttachmentEditor> initialRules
    ) {
        return create(subjectId, key, 0, initialRules);
    }

    default IPermissionAttachment create(
            UUID subjectId,
            String key,
            int priority,
            Consumer<IAttachmentEditor> initialRules
    ) {
        IUserAttachments attachments = forUser(subjectId)
                .orElseThrow(() -> new IllegalStateException("User is not loaded: " + subjectId));
        return attachments.create(key, priority, initialRules);
    }

    boolean invalidateAll(UUID subjectId);
    int invalidateNamespace(String namespace);
}
