package de.astranox.nixperms.core.config;

import de.astranox.nixperms.api.permission.ResolutionPolicy;

import java.util.Locale;

public record NixPermsSettings(
        DatabaseSettings database,
        NetworkSettings network,
        String defaultGroup,
        ResolutionPolicy resolutionPolicy,
        int databaseThreads
) {

    public NixPermsSettings {
        if (database == null) throw new IllegalArgumentException("Database settings cannot be null");
        if (network == null) throw new IllegalArgumentException("Network settings cannot be null");
        if (defaultGroup == null || defaultGroup.isBlank()) defaultGroup = "default";
        defaultGroup = defaultGroup.trim().toLowerCase(Locale.ROOT);
        if (!defaultGroup.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Invalid default group: " + defaultGroup);
        }
        if (resolutionPolicy == null) resolutionPolicy = ResolutionPolicy.PRIMARY_WINS;
        if (databaseThreads < 1 || databaseThreads > 64) {
            throw new IllegalArgumentException("Database threads must be between 1 and 64");
        }
    }
}
