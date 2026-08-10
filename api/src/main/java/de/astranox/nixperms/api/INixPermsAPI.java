package de.astranox.nixperms.api;

import de.astranox.nixperms.api.attachment.IAttachmentService;
import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.group.IGroupManager;
import de.astranox.nixperms.api.message.IMessageService;
import de.astranox.nixperms.api.sync.ISyncNotifier;
import de.astranox.nixperms.api.user.IUserManager;

public interface INixPermsAPI {
    String version();
    String serverId();
    IUserManager users();
    IGroupManager groups();
    IAttachmentService attachments();
    IEventBus events();
    IMessageService messages();
    ISyncNotifier syncNotifier();
}
