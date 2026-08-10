package de.astranox.nixperms.core.permission;

import de.astranox.nixperms.api.attachment.IPermissionAttachment;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.ResolutionPolicy;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Builds immutable snapshots. It is never called from the permission hot path. */
public final class NixPermissionResolver {

    private static final Comparator<IPermissionAttachment> ATTACHMENT_ORDER =
            Comparator.comparingInt(IPermissionAttachment::priority)
                    .thenComparingLong(IPermissionAttachment::sequence);

    private final Function<IPermissionGroup, List<IPermissionGroup>> chainProvider;

    public NixPermissionResolver(Function<IPermissionGroup, List<IPermissionGroup>> chainProvider) {
        this.chainProvider = chainProvider;
    }

    public NixPermissionData compute(
            IPermissionGroup primary,
            @Nullable IPermissionGroup secondary,
            Collection<PermissionRule> ownRules,
            Collection<IPermissionAttachment> attachments,
            PermissionContext context,
            ResolutionPolicy policy
    ) {
        Map<String, PermissionDecision> primaryValues = flattenChain(primary, context);
        Map<String, PermissionDecision> secondaryValues = secondary == null
                ? Map.of()
                : flattenChain(secondary, context);
        Map<String, PermissionDecision> ownValues = scoped(ownRules, context);
        List<Map<String, PermissionDecision>> attachmentValues = attachmentLayers(attachments, context);

        PermissionNodeIndex primaryIndex = new PermissionNodeIndex(primaryValues);
        PermissionNodeIndex secondaryIndex = new PermissionNodeIndex(secondaryValues);
        PermissionNodeIndex ownIndex = new PermissionNodeIndex(ownValues);
        List<PermissionNodeIndex> attachmentIndexes = attachmentValues.stream()
                .map(PermissionNodeIndex::new)
                .toList();

        DecisionResolver resolver = node -> resolve(
                node, primaryIndex, secondaryIndex, ownIndex, attachmentIndexes, policy
        );
        Map<String, PermissionDecision> effective = materialize(
                primaryValues, secondaryValues, ownValues, attachmentValues, resolver
        );
        // The materialized map contains every rule boundary with its final decision.
        // Permission checks therefore only need one exact/wildcard lookup, regardless
        // of inheritance depth or attachment count.
        return new NixPermissionData(context, effective);
    }

    private Map<String, PermissionDecision> flattenChain(
            IPermissionGroup group,
            PermissionContext context
    ) {
        Map<String, PermissionDecision> result = new HashMap<>();
        for (IPermissionGroup entry : chainProvider.apply(group)) {
            overlay(result, scoped(entry.rules(), context));
        }
        return Map.copyOf(result);
    }

    private Map<String, PermissionDecision> scoped(
            Collection<PermissionRule> rules,
            PermissionContext context
    ) {
        if (rules == null || rules.isEmpty()) return Map.of();
        Map<String, PermissionDecision> result = new HashMap<>();
        for (int precedence = 0; precedence <= 3; precedence++) {
            Map<String, PermissionDecision> layer = new HashMap<>();
            for (PermissionRule rule : rules) {
                if (rule.scope().specificity() != precedence) continue;
                if (rule.scope().matches(context)) layer.put(rule.node(), rule.decision());
            }
            overlay(result, layer);
        }
        return Map.copyOf(result);
    }

    private List<Map<String, PermissionDecision>> attachmentLayers(
            Collection<IPermissionAttachment> attachments,
            PermissionContext context
    ) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        List<IPermissionAttachment> ordered = new ArrayList<>(attachments);
        ordered.removeIf(attachment -> !attachment.active());
        ordered.sort(ATTACHMENT_ORDER);
        return ordered.stream().map(attachment -> scoped(attachment.rules(), context)).toList();
    }

    private PermissionDecision resolve(
            String node,
            PermissionNodeIndex primary,
            PermissionNodeIndex secondary,
            PermissionNodeIndex own,
            List<PermissionNodeIndex> attachments,
            ResolutionPolicy policy
    ) {
        PermissionDecision result = resolveGroups(node, primary, secondary, policy);
        PermissionDecision ownDecision = own.decision(node);
        if (ownDecision.isSet()) result = ownDecision;
        for (PermissionNodeIndex attachment : attachments) {
            PermissionDecision decision = attachment.decision(node);
            if (decision.isSet()) result = decision;
        }
        return result;
    }

    private PermissionDecision resolveGroups(
            String node,
            PermissionNodeIndex primary,
            PermissionNodeIndex secondary,
            ResolutionPolicy policy
    ) {
        PermissionDecision primaryDecision = primary.decision(node);
        PermissionDecision secondaryDecision = secondary.decision(node);
        return switch (policy) {
            case PRIMARY_WINS -> primaryDecision.isSet() ? primaryDecision : secondaryDecision;
            case SECONDARY_WINS -> secondaryDecision.isSet() ? secondaryDecision : primaryDecision;
            case DENY_WINS -> {
                if (primaryDecision == PermissionDecision.DENY || secondaryDecision == PermissionDecision.DENY) {
                    yield PermissionDecision.DENY;
                }
                if (primaryDecision == PermissionDecision.ALLOW || secondaryDecision == PermissionDecision.ALLOW) {
                    yield PermissionDecision.ALLOW;
                }
                yield PermissionDecision.UNSET;
            }
        };
    }

    private Map<String, PermissionDecision> materialize(
            Map<String, PermissionDecision> primary,
            Map<String, PermissionDecision> secondary,
            Map<String, PermissionDecision> own,
            List<Map<String, PermissionDecision>> attachments,
            DecisionResolver resolver
    ) {
        Set<String> nodes = new LinkedHashSet<>();
        nodes.addAll(primary.keySet());
        nodes.addAll(secondary.keySet());
        nodes.addAll(own.keySet());
        attachments.forEach(layer -> nodes.addAll(layer.keySet()));

        Map<String, PermissionDecision> result = new HashMap<>();
        for (String node : nodes) {
            PermissionDecision decision = resolver.decision(node);
            if (decision.isSet()) result.put(node, decision);
        }
        return result;
    }

    private void overlay(
            Map<String, PermissionDecision> target,
            Map<String, PermissionDecision> higherPriority
    ) {
        if (higherPriority.isEmpty()) return;
        if (higherPriority.containsKey("*")) {
            target.clear();
        } else {
            for (String node : higherPriority.keySet()) {
                if (!node.endsWith(".*")) continue;
                String prefix = node.substring(0, node.length() - 1);
                target.keySet().removeIf(existing -> existing.startsWith(prefix));
            }
        }
        target.putAll(higherPriority);
    }

    @FunctionalInterface
    private interface DecisionResolver {
        PermissionDecision decision(String node);
    }
}
