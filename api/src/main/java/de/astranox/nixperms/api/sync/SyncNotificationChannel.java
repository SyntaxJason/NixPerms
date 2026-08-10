package de.astranox.nixperms.api.sync;

import de.astranox.nixperms.api.message.IMessageService;

@FunctionalInterface
public interface SyncNotificationChannel {
    void dispatch(SyncNotification notification, IMessageService messages);
}
