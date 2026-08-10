package de.astranox.nixperms.api.permission;

/** The tri-state result of a permission lookup. */
public enum PermissionDecision {
    ALLOW,
    DENY,
    UNSET;

    public boolean allowed() {
        return this == ALLOW;
    }

    public boolean isSet() {
        return this != UNSET;
    }

    public static PermissionDecision of(boolean value) {
        return value ? ALLOW : DENY;
    }
}
