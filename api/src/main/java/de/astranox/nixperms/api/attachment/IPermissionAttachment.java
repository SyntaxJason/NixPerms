package de.astranox.nixperms.api.attachment;

import de.astranox.nixperms.api.permission.PermissionRule;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

/** A runtime permission overlay. Closing it removes exactly this attachment. */
public interface IPermissionAttachment extends AutoCloseable {
    UUID uniqueId();
    UUID subjectId();
    String key();
    int priority();
    long sequence();
    boolean active();
    Collection<PermissionRule> rules();
    IPermissionAttachment edit(Consumer<IAttachmentEditor> editor);

    @Override
    void close();
}
