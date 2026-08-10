package de.astranox.nixperms.api.permission;

import java.util.Locale;

/**
 * A fixed permission scope. Empty fields are wildcards; no arbitrary context
 * map is evaluated in the permission hot path.
 */
public record PermissionScope(String server, String world) {

    public static final String ANY = "";
    public static final PermissionScope GLOBAL = new PermissionScope(ANY, ANY);

    public PermissionScope {
        server = normalize(server);
        world = normalize(world);
    }

    public static PermissionScope server(String server) {
        return new PermissionScope(server, ANY);
    }

    public static PermissionScope world(String world) {
        return new PermissionScope(ANY, world);
    }

    public static PermissionScope serverWorld(String server, String world) {
        return new PermissionScope(server, world);
    }

    public boolean matches(PermissionContext context) {
        if (context == null) return this.equals(GLOBAL);
        if (!server.isEmpty() && !server.equals(context.server())) return false;
        return world.isEmpty() || world.equals(context.world());
    }

    public int specificity() {
        if (!server.isEmpty() && !world.isEmpty()) return 3;
        if (!world.isEmpty()) return 2;
        if (!server.isEmpty()) return 1;
        return 0;
    }

    private static String normalize(String value) {
        if (value == null) return ANY;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("Scope value is longer than 64 characters");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException("Scope value contains control characters");
            }
        }
        return normalized;
    }
}
