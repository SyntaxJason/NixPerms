package de.astranox.nixperms.core.permission;

import de.astranox.nixperms.api.permission.IPermissionEditor;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.PermissionScope;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NixPermissionEditor<T extends NixPermissionEditor<T>> implements IPermissionEditor {

    protected final Map<RuleKey, PermissionDecision> rules = new LinkedHashMap<>();

    public NixPermissionEditor(Collection<PermissionRule> initialRules) {
        if (initialRules == null) return;
        for (PermissionRule rule : initialRules) {
            rules.put(new RuleKey(rule.node(), rule.scope()), rule.decision());
        }
    }

    @Override
    public T allow(String node, PermissionScope scope) {
        put(node, scope, PermissionDecision.ALLOW);
        return self();
    }

    @Override
    public T deny(String node, PermissionScope scope) {
        put(node, scope, PermissionDecision.DENY);
        return self();
    }

    @Override
    public T unset(String node, PermissionScope scope) {
        rules.remove(key(node, scope));
        return self();
    }

    @Override
    public T clearPermissions() {
        rules.clear();
        return self();
    }

    public List<PermissionRule> buildRules() {
        return rules.entrySet().stream()
                .map(entry -> new PermissionRule(
                        entry.getKey().node(),
                        entry.getValue(),
                        entry.getKey().scope()
                ))
                .toList();
    }

    protected final void put(String node, PermissionScope scope, PermissionDecision decision) {
        rules.put(key(node, scope), decision);
    }

    protected final RuleKey key(String node, PermissionScope scope) {
        PermissionScope safeScope = scope == null ? PermissionScope.GLOBAL : scope;
        String normalized = PermissionRule.normalizeNode(node);
        PermissionRule.validateNode(normalized);
        return new RuleKey(normalized, safeScope);
    }

    protected record RuleKey(String node, PermissionScope scope) { }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }
}
