package de.astranox.nixperms.core.attachment;

import de.astranox.nixperms.api.attachment.IAttachmentEditor;
import de.astranox.nixperms.api.attachment.IPermissionAttachment;
import de.astranox.nixperms.api.attachment.IUserAttachments;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class NixUserAttachments implements IUserAttachments {

    private final UUID subjectId;
    private final Runnable refreshCallback;
    private final ConcurrentHashMap<String, NixPermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public NixUserAttachments(UUID subjectId, Runnable refreshCallback) {
        this.subjectId = subjectId;
        this.refreshCallback = refreshCallback;
    }

    @Override
    public synchronized IPermissionAttachment create(
            String key,
            int priority,
            Consumer<IAttachmentEditor> initialRules
    ) {
        String normalized = normalizeKey(key);
        if (initialRules == null) throw new IllegalArgumentException("Initial rules cannot be null");

        NixAttachmentEditor editor = new NixAttachmentEditor(List.of());
        initialRules.accept(editor);
        NixPermissionAttachment created = new NixPermissionAttachment(
                subjectId, normalized, priority, sequence.incrementAndGet(), editor.buildRules(), this
        );

        NixPermissionAttachment previous = attachments.put(normalized, created);
        if (previous != null) previous.deactivate();
        refresh();
        return created;
    }

    @Override
    public Optional<IPermissionAttachment> get(String key) {
        return Optional.ofNullable(attachments.get(normalizeKey(key)));
    }

    @Override
    public Collection<IPermissionAttachment> all() {
        return List.copyOf(attachments.values());
    }

    public Collection<IPermissionAttachment> snapshot() {
        return List.copyOf(attachments.values());
    }

    @Override
    public synchronized boolean remove(String key) {
        NixPermissionAttachment removed = attachments.remove(normalizeKey(key));
        if (removed == null) return false;
        removed.deactivate();
        refresh();
        return true;
    }

    synchronized boolean remove(NixPermissionAttachment attachment) {
        boolean removed = attachments.remove(attachment.key(), attachment);
        if (!removed) return false;
        attachment.deactivate();
        refresh();
        return true;
    }

    @Override
    public synchronized int removeNamespace(String namespace) {
        String normalized = normalizeNamespace(namespace);
        int removed = 0;
        for (NixPermissionAttachment attachment : attachments.values()) {
            if (!namespaceOf(attachment.key()).equals(normalized)) continue;
            if (attachments.remove(attachment.key(), attachment)) {
                attachment.deactivate();
                removed++;
            }
        }
        if (removed > 0) refresh();
        return removed;
    }

    @Override
    public synchronized void clear() {
        if (attachments.isEmpty()) return;
        attachments.values().forEach(NixPermissionAttachment::deactivate);
        attachments.clear();
        refresh();
    }

    void refresh() {
        refreshCallback.run();
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Attachment key cannot be blank");
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}(:[a-z0-9][a-z0-9._/-]{0,63})?")) {
            throw new IllegalArgumentException("Invalid attachment key: " + key);
        }
        return normalized;
    }

    private String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("Namespace cannot be blank");
        String normalized = namespace.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Invalid attachment namespace: " + namespace);
        }
        return normalized;
    }

    private String namespaceOf(String key) {
        int separator = key.indexOf(':');
        return separator < 0 ? key : key.substring(0, separator);
    }
}
