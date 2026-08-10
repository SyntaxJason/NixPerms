package de.astranox.nixperms.api.group;

import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;

import java.util.Collection;
import java.util.Map;

/** Immutable permission rules owned directly by a group. */
public interface IGroupPermissionData {
    Collection<PermissionRule> rules();
    Map<String, Boolean> global();
    PermissionDecision globalDecision(String node);

    default Map<String, Boolean> asMap() {
        return global();
    }

    default boolean contains(String node) {
        return globalDecision(node).isSet();
    }

    default boolean get(String node) {
        return globalDecision(node).allowed();
    }
}
