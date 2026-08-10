package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.group.IGroupPermissionData;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.PermissionScope;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NixGroupPermissionData implements IGroupPermissionData {

    private final Collection<PermissionRule> rules;
    private final Map<String, Boolean> global;
    private final Map<String, PermissionDecision> globalDecisions;

    public NixGroupPermissionData(Collection<PermissionRule> source) {
        this.rules = ListCopy.copy(source);

        Map<String, Boolean> booleans = new LinkedHashMap<>();
        Map<String, PermissionDecision> decisions = new LinkedHashMap<>();
        for (PermissionRule rule : this.rules) {
            if (!PermissionScope.GLOBAL.equals(rule.scope())) continue;
            decisions.put(rule.node(), rule.decision());
            booleans.put(rule.node(), rule.decision().allowed());
        }
        this.global = Map.copyOf(booleans);
        this.globalDecisions = Map.copyOf(decisions);
    }

    @Override public Collection<PermissionRule> rules() { return rules; }
    @Override public Map<String, Boolean> global() { return global; }

    @Override
    public PermissionDecision globalDecision(String node) {
        return globalDecisions.getOrDefault(
                PermissionRule.normalizeNode(node),
                PermissionDecision.UNSET
        );
    }

    private static final class ListCopy {
        private ListCopy() { }

        private static <T> Collection<T> copy(Collection<T> source) {
            if (source == null || source.isEmpty()) return java.util.List.of();
            return java.util.List.copyOf(source);
        }
    }
}
