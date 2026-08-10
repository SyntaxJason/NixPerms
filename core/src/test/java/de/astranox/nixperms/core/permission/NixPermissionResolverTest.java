package de.astranox.nixperms.core.permission;

import de.astranox.nixperms.api.attachment.IAttachmentEditor;
import de.astranox.nixperms.api.attachment.IPermissionAttachment;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IGroupEditor;
import de.astranox.nixperms.api.group.IGroupMeta;
import de.astranox.nixperms.api.group.IGroupPermissionData;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.PermissionScope;
import de.astranox.nixperms.api.permission.ResolutionPolicy;
import de.astranox.nixperms.core.group.NixGroupMeta;
import de.astranox.nixperms.core.group.NixGroupPermissionData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NixPermissionResolverTest {

    private final NixPermissionResolver resolver = new NixPermissionResolver(this::chain);

    @Test
    void resolvesFixedScopesInDeterministicOrder() {
        TestGroup primary = group("default", List.of(
                rule("chat.send", false, PermissionScope.server("hub")),
                rule("chat.send", true, PermissionScope.world("nether")),
                rule("admin.kick", false, PermissionScope.GLOBAL),
                rule("admin.*", true, PermissionScope.world("nether"))
        ));

        NixPermissionData data = compute(primary, null, List.of(), List.of(), ResolutionPolicy.PRIMARY_WINS);

        assertEquals(PermissionDecision.ALLOW, data.decision("chat.send"));
        assertEquals(PermissionDecision.ALLOW, data.decision("admin.kick"));
    }

    @Test
    void primaryPolicyAppliesAfterWildcardResolution() {
        TestGroup primary = group("primary", List.of(PermissionRule.allow("feature.*")));
        TestGroup secondary = secondary("secondary", List.of(PermissionRule.deny("feature.command")));

        NixPermissionData primaryWins = compute(
                primary, secondary, List.of(), List.of(), ResolutionPolicy.PRIMARY_WINS
        );
        NixPermissionData denyWins = compute(
                primary, secondary, List.of(), List.of(), ResolutionPolicy.DENY_WINS
        );

        assertEquals(PermissionDecision.ALLOW, primaryWins.decision("feature.command"));
        assertEquals(PermissionDecision.DENY, denyWins.decision("feature.command"));
        assertEquals(PermissionDecision.ALLOW, denyWins.decision("feature.other"));
    }

    @Test
    void compactSnapshotStillResolvesNodesThatWereNotMaterialized() {
        TestGroup primary = group("primary", List.of(PermissionRule.allow("quests.*")));
        List<PermissionRule> own = List.of(PermissionRule.deny("quests.admin.*"));

        NixPermissionData data = compute(
                primary, null, own, List.of(), ResolutionPolicy.PRIMARY_WINS
        );

        assertEquals(PermissionDecision.ALLOW, data.decision("quests.daily.claim"));
        assertEquals(PermissionDecision.DENY, data.decision("quests.admin.reload"));
    }

    @Test
    void userAndHigherAttachmentLayersOverrideLowerExactRules() {
        TestGroup primary = group("primary", List.of(PermissionRule.allow("flight.use")));
        List<PermissionRule> own = List.of(PermissionRule.deny("flight.*"));
        TestAttachment low = new TestAttachment("plugin:low", 1, 1, List.of(PermissionRule.allow("flight.use")));
        TestAttachment high = new TestAttachment("plugin:high", 10, 2, List.of(PermissionRule.deny("flight.*")));

        NixPermissionData data = compute(
                primary, null, own, List.of(high, low), ResolutionPolicy.PRIMARY_WINS
        );

        assertEquals(PermissionDecision.DENY, data.decision("flight.use"));
    }

    @Test
    void childWildcardOverridesInheritedExactRule() {
        TestGroup parent = group("parent", List.of(PermissionRule.deny("build.break")));
        TestGroup child = new TestGroup(
                "child", GroupRole.PRIMARY, List.of(PermissionRule.allow("build.*")), parent
        );

        NixPermissionData data = compute(child, null, List.of(), List.of(), ResolutionPolicy.PRIMARY_WINS);

        assertEquals(PermissionDecision.ALLOW, data.decision("build.break"));
    }

    private NixPermissionData compute(
            TestGroup primary,
            TestGroup secondary,
            Collection<PermissionRule> own,
            Collection<IPermissionAttachment> attachments,
            ResolutionPolicy policy
    ) {
        return resolver.compute(
                primary, secondary, own, attachments,
                new PermissionContext("hub", "nether"), policy
        );
    }

    private List<IPermissionGroup> chain(IPermissionGroup root) {
        List<IPermissionGroup> result = new ArrayList<>();
        IPermissionGroup current = root;
        while (current != null) {
            result.add(current);
            current = current.parent().orElse(null);
        }
        Collections.reverse(result);
        return result;
    }

    private TestGroup group(String name, List<PermissionRule> rules) {
        return new TestGroup(name, GroupRole.PRIMARY, rules, null);
    }

    private TestGroup secondary(String name, List<PermissionRule> rules) {
        return new TestGroup(name, GroupRole.SECONDARY, rules, null);
    }

    private PermissionRule rule(String node, boolean value, PermissionScope scope) {
        return new PermissionRule(node, PermissionDecision.of(value), scope);
    }

    private record TestGroup(
            String name,
            GroupRole role,
            List<PermissionRule> rules,
            TestGroup inherited
    ) implements IPermissionGroup {
        @Override public int weight() { return 0; }
        @Override public IGroupPermissionData permissions() { return new NixGroupPermissionData(rules); }
        @Override public IGroupMeta meta() { return new NixGroupMeta(Map.of(), List.of(), List.of()); }
        @Override public Optional<IPermissionGroup> parent() { return Optional.ofNullable(inherited); }
        @Override public Optional<IPermissionGroup> defaultSecondary() { return Optional.empty(); }
        @Override public CompletableFuture<IPermissionGroup> edit(Consumer<IGroupEditor> editor) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }

    private static final class TestAttachment implements IPermissionAttachment {
        private final UUID uniqueId = UUID.randomUUID();
        private final String key;
        private final int priority;
        private final long sequence;
        private final List<PermissionRule> rules;

        private TestAttachment(String key, int priority, long sequence, List<PermissionRule> rules) {
            this.key = key;
            this.priority = priority;
            this.sequence = sequence;
            this.rules = rules;
        }

        @Override public UUID uniqueId() { return uniqueId; }
        @Override public UUID subjectId() { return new UUID(0L, 0L); }
        @Override public String key() { return key; }
        @Override public int priority() { return priority; }
        @Override public long sequence() { return sequence; }
        @Override public boolean active() { return true; }
        @Override public Collection<PermissionRule> rules() { return rules; }
        @Override public IPermissionAttachment edit(Consumer<IAttachmentEditor> editor) { return this; }
        @Override public void close() { }
    }
}
