package de.astranox.nixperms.core.permission;

import de.astranox.nixperms.api.permission.IPermissionData;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable, lock-free permission snapshot. */
public final class NixPermissionData implements IPermissionData {

    private final PermissionContext context;
    private final Map<String, PermissionDecision> effective;
    private final Map<String, Boolean> flattened;
    private final PermissionNodeIndex index;

    public NixPermissionData(
            PermissionContext context,
            Map<String, PermissionDecision> effective
    ) {
        this.context = context == null ? PermissionContext.GLOBAL : context;
        this.effective = Map.copyOf(effective);
        this.index = new PermissionNodeIndex(this.effective);

        Map<String, Boolean> values = new LinkedHashMap<>(effective.size());
        effective.forEach((node, decision) -> values.put(node, decision.allowed()));
        this.flattened = Map.copyOf(values);
    }

    @Override
    public PermissionContext context() {
        return context;
    }

    @Override
    public PermissionDecision decision(String node) {
        String normalized = normalizedLookupNode(node);
        if (normalized.isEmpty()) return PermissionDecision.UNSET;
        return index.decision(normalized);
    }

    static PermissionDecision resolve(Map<String, PermissionDecision> values, String normalized) {
        if (normalized == null || normalized.isEmpty()) return PermissionDecision.UNSET;
        return new PermissionNodeIndex(values).decision(normalized);
    }

    @Override
    public Map<String, PermissionDecision> effective() {
        return effective;
    }

    @Override
    public Map<String, Boolean> flattened() {
        return flattened;
    }

    private static String normalizedLookupNode(String node) {
        if (node == null || node.isEmpty()) return "";

        int start = 0;
        int end = node.length();
        while (start < end && Character.isWhitespace(node.charAt(start))) start++;
        while (end > start && Character.isWhitespace(node.charAt(end - 1))) end--;
        if (start == end) return "";

        boolean lowerCase = true;
        for (int index = start; index < end; index++) {
            char current = node.charAt(index);
            if (Character.toLowerCase(current) != current) {
                lowerCase = false;
                break;
            }
        }

        if (start == 0 && end == node.length() && lowerCase) return node;
        return node.substring(start, end).toLowerCase(Locale.ROOT);
    }
}
