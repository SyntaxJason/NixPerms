package de.astranox.nixperms.api.sync;

import java.util.UUID;

public interface ISyncProvider {
    void start();
    void stop();
    void publishUserUpdate(UUID userId);
    void publishGroupUpdate(String groupName);
    void publishGlobalInvalidation();
}
