package de.astranox.nixperms.api.permission;

import java.util.Map;

/** Immutable effective permission data for one runtime context. */
public interface IPermissionData {
    PermissionContext context();
    PermissionDecision decision(String node);
    Map<String, PermissionDecision> effective();
    Map<String, Boolean> flattened();

    default boolean has(String node) {
        return decision(node).allowed();
    }
}
