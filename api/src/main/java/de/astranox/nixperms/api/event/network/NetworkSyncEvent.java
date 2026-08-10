package de.astranox.nixperms.api.event.network;

import de.astranox.nixperms.api.event.INixEvent;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public record NetworkSyncEvent(SyncType syncType, @Nullable UUID affectedUserId, @Nullable String affectedGroupName, String sourceServerId) implements INixEvent {
    public enum SyncType { USER_UPDATE, GROUP_UPDATE, GLOBAL_INVALIDATION }
}
