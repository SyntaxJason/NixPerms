package de.astranox.nixperms.web.auth;

import java.util.UUID;

public record SessionToken(UUID playerUuid, String token, long expiresAt) {
    public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
}
