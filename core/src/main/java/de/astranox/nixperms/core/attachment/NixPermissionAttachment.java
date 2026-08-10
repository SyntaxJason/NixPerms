package de.astranox.nixperms.core.attachment;

import de.astranox.nixperms.api.attachment.IAttachmentEditor;
import de.astranox.nixperms.api.attachment.IPermissionAttachment;
import de.astranox.nixperms.api.permission.PermissionRule;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class NixPermissionAttachment implements IPermissionAttachment {

    private final UUID uniqueId = UUID.randomUUID();
    private final UUID subjectId;
    private final String key;
    private final int priority;
    private final long sequence;
    private final NixUserAttachments owner;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private volatile List<PermissionRule> rules;

    NixPermissionAttachment(
            UUID subjectId,
            String key,
            int priority,
            long sequence,
            Collection<PermissionRule> initialRules,
            NixUserAttachments owner
    ) {
        this.subjectId = subjectId;
        this.key = key;
        this.priority = priority;
        this.sequence = sequence;
        this.rules = List.copyOf(initialRules);
        this.owner = owner;
    }

    @Override public UUID uniqueId() { return uniqueId; }
    @Override public UUID subjectId() { return subjectId; }
    @Override public String key() { return key; }
    @Override public int priority() { return priority; }
    @Override public long sequence() { return sequence; }
    @Override public boolean active() { return active.get(); }
    @Override public Collection<PermissionRule> rules() { return rules; }

    @Override
    public synchronized IPermissionAttachment edit(Consumer<IAttachmentEditor> change) {
        if (!active()) throw new IllegalStateException("Attachment is no longer active: " + key);
        if (change == null) throw new IllegalArgumentException("Attachment editor cannot be null");
        NixAttachmentEditor editor = new NixAttachmentEditor(rules);
        change.accept(editor);
        rules = editor.buildRules();
        owner.refresh();
        return this;
    }

    @Override
    public void close() {
        owner.remove(this);
    }

    void deactivate() {
        active.set(false);
    }
}
