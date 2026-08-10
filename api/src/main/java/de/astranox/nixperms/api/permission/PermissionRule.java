package de.astranox.nixperms.api.permission;

import java.util.Locale;

/** A persisted or temporary permission rule. */
public record PermissionRule(
        String node,
        PermissionDecision decision,
        PermissionScope scope
) {

    public PermissionRule {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("Permission node cannot be blank");
        }
        if (decision == null || decision == PermissionDecision.UNSET) {
            throw new IllegalArgumentException("A stored permission rule must ALLOW or DENY");
        }
        if (scope == null) scope = PermissionScope.GLOBAL;
        node = normalizeNode(node);
        validateNode(node);
    }

    public static PermissionRule allow(String node) {
        return new PermissionRule(node, PermissionDecision.ALLOW, PermissionScope.GLOBAL);
    }

    public static PermissionRule deny(String node) {
        return new PermissionRule(node, PermissionDecision.DENY, PermissionScope.GLOBAL);
    }

    public static String normalizeNode(String node) {
        if (node == null) return "";
        return node.trim().toLowerCase(Locale.ROOT);
    }

    public static void validateNode(String node) {
        if (node.isEmpty()) throw new IllegalArgumentException("Permission node cannot be blank");
        if (node.length() > 256) throw new IllegalArgumentException("Permission node is longer than 256 characters");
        for (int index = 0; index < node.length(); index++) {
            char current = node.charAt(index);
            if (Character.isWhitespace(current) || Character.isISOControl(current)) {
                throw new IllegalArgumentException("Permission node contains whitespace or control characters");
            }
        }
        int wildcard = node.indexOf('*');
        if (wildcard < 0) return;
        boolean valid = node.equals("*") ||
                (wildcard == node.length() - 1 && wildcard > 0 && node.charAt(wildcard - 1) == '.');
        if (!valid || node.indexOf('*', wildcard + 1) >= 0) {
            throw new IllegalArgumentException("Wildcard must be '*' or the final segment of a permission node");
        }
    }
}
