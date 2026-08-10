package de.astranox.nixperms.core.attachment;

import de.astranox.nixperms.api.attachment.IPermissionAttachment;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NixUserAttachmentsTest {

    @Test
    void closingReplacedHandleDoesNotRemoveReplacement() {
        AtomicInteger refreshes = new AtomicInteger();
        NixUserAttachments attachments = new NixUserAttachments(UUID.randomUUID(), refreshes::incrementAndGet);
        IPermissionAttachment first = attachments.create("quests:session", editor -> editor.allow("quest.start"));
        IPermissionAttachment replacement = attachments.create("quests:session", editor -> editor.deny("quest.start"));

        first.close();

        assertFalse(first.active());
        assertTrue(replacement.active());
        assertEquals(replacement.uniqueId(), attachments.get("quests:session").orElseThrow().uniqueId());
        assertEquals(2, refreshes.get());
    }

    @Test
    void namespaceInvalidationOnlyRemovesMatchingAttachments() {
        NixUserAttachments attachments = new NixUserAttachments(UUID.randomUUID(), () -> { });
        attachments.create("quests:first");
        attachments.create("quests:second");
        attachments.create("combat:session");

        assertEquals(2, attachments.removeNamespace("quests"));
        assertEquals(1, attachments.all().size());
        assertTrue(attachments.get("combat:session").isPresent());
    }
}
