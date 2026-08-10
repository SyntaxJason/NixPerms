package de.astranox.nixperms.core.profile;

import java.util.UUID;

public record ResolvedProfile(UUID uniqueId, String name) {

    public ResolvedProfile {
        if (uniqueId == null) throw new IllegalArgumentException("Profile UUID cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Profile name cannot be blank");
        name = name.trim();
    }
}
