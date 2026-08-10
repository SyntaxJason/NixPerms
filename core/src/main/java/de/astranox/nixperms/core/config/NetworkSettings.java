package de.astranox.nixperms.core.config;

import java.time.Duration;
import java.util.Locale;

public record NetworkSettings(
        boolean enabled,
        String serverId,
        Duration pollInterval,
        Duration retention
) {

    public NetworkSettings {
        if (serverId == null || serverId.isBlank()) throw new IllegalArgumentException("Server ID cannot be blank");
        serverId = serverId.trim().toLowerCase(Locale.ROOT);
        if (!serverId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Invalid server ID: " + serverId);
        }
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("Poll interval must be positive");
        }
        if (retention == null || retention.compareTo(pollInterval) <= 0) {
            throw new IllegalArgumentException("Sync retention must be longer than the poll interval");
        }
    }
}
