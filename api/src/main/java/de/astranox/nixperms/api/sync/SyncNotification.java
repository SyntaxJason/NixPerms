package de.astranox.nixperms.api.sync;

import de.astranox.nixperms.api.platform.Platform;
import java.util.EnumSet;
import java.util.Map;

public record SyncNotification(String messageKey, Map<String, Object> placeholders, EnumSet<Platform> targets) {}
