package de.astranox.nixperms.core.permission;

import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NixPermissionDataTest {

    @Test
    void resolvesExactAndLongestWildcardWithoutBuildingWildcardStrings() {
        NixPermissionData data = new NixPermissionData(PermissionContext.GLOBAL, Map.of(
                "*", PermissionDecision.DENY,
                "essentials.*", PermissionDecision.ALLOW,
                "essentials.gamemode.*", PermissionDecision.DENY,
                "essentials.gamemode.creative", PermissionDecision.ALLOW
        ));

        assertEquals(PermissionDecision.DENY, data.decision("other.node"));
        assertEquals(PermissionDecision.ALLOW, data.decision("essentials.home"));
        assertEquals(PermissionDecision.DENY, data.decision("essentials.gamemode.survival"));
        assertEquals(PermissionDecision.ALLOW, data.decision("essentials.gamemode.creative"));
    }

    @Test
    void avoidsNormalizationAllocationForTheNormalLowercasePathButStillAcceptsMixedInput() {
        NixPermissionData data = new NixPermissionData(PermissionContext.GLOBAL, Map.of(
                "example.node", PermissionDecision.ALLOW
        ));

        assertEquals(PermissionDecision.ALLOW, data.decision("example.node"));
        assertEquals(PermissionDecision.ALLOW, data.decision("  EXAMPLE.NODE  "));
    }
}
