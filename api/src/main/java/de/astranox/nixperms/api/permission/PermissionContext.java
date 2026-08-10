package de.astranox.nixperms.api.permission;

import java.util.Locale;

/** The small, fixed runtime context used by NixPerms. */
public record PermissionContext(String server, String world) {

    public static final String ANY = "";
    public static final PermissionContext GLOBAL = new PermissionContext(ANY, ANY);

    public PermissionContext {
        server = normalize(server);
        world = normalize(world);
    }

    public static PermissionContext server(String server) {
        return new PermissionContext(server, ANY);
    }

    public static PermissionContext of(String server, String world) {
        return new PermissionContext(server, world);
    }

    private static String normalize(String value) {
        if (value == null) return ANY;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        validate(normalized);
        return normalized;
    }

    private static void validate(String value) {
        if (value.length() > 64) throw new IllegalArgumentException("Context value is longer than 64 characters");
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("Context value contains control characters");
            }
        }
    }
}
