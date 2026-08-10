package de.astranox.nixperms.api.sync;

import de.astranox.nixperms.api.platform.Platform;

public interface ISyncNotifier {
    void notify(SyncNotification notification);
    void registerChannel(Platform platform, SyncNotificationChannel channel);
    void unregisterChannel(Platform platform);
}
