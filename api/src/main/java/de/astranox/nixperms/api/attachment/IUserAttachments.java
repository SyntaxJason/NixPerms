package de.astranox.nixperms.api.attachment;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

/** Runtime-only attachments belonging to one loaded user. */
public interface IUserAttachments {

    default IPermissionAttachment create(String key) {
        return create(key, 0, editor -> { });
    }

    default IPermissionAttachment create(String key, Consumer<IAttachmentEditor> initialRules) {
        return create(key, 0, initialRules);
    }

    IPermissionAttachment create(String key, int priority, Consumer<IAttachmentEditor> initialRules);
    Optional<IPermissionAttachment> get(String key);
    Collection<IPermissionAttachment> all();
    boolean remove(String key);
    int removeNamespace(String namespace);
    void clear();
}
