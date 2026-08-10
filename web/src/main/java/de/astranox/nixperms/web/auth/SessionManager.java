package de.astranox.nixperms.web.auth;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Optional;
import java.util.UUID;

public final class SessionManager {

    private final long ttlMs;
    private final Object2ObjectOpenHashMap<String, SessionToken> tokens = new Object2ObjectOpenHashMap<>();

    public SessionManager(int ttlMinutes) {
        this.ttlMs = ttlMinutes * 60_000L;
    }

    public SessionToken generate(UUID playerUuid) {
        String token = "nxp_" + UUID.randomUUID().toString().replace("-", "");
        SessionToken session = new SessionToken(playerUuid, token, System.currentTimeMillis() + ttlMs);
        tokens.put(token, session);
        return session;
    }

    public Optional<SessionToken> validate(String token) {
        if (token == null) return Optional.empty();
        SessionToken session = tokens.get(token);
        if (session == null) return Optional.empty();
        if (session.isExpired()) { tokens.remove(token); return Optional.empty(); }
        return Optional.of(session);
    }

    public void invalidate(UUID playerUuid) {
        tokens.values().removeIf(s -> s.playerUuid().equals(playerUuid));
    }

    public void purgeExpired() {
        tokens.values().removeIf(SessionToken::isExpired);
    }
}
