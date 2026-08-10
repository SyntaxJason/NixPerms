package de.astranox.nixperms.core.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NixCommandSuggestionsTest {

    private static final List<String> NODES = List.of(
            "essentials.gamemode.creative",
            "essentials.gamemode.survival",
            "essentials.home",
            "minecraft.command.gamemode",
            "minecraft.command.teleport",
            "nixperms.admin"
    );

    @Test
    void returnsOnlyPermissionRootsForAnEmptyNode() {
        assertEquals(
                List.of("essentials.", "minecraft.", "nixperms."),
                NixCommandSuggestions.filterPermissionTree(NODES, "")
        );
    }

    @Test
    void returnsOnlyTheNextPermissionSegment() {
        assertEquals(
                List.of("essentials.gamemode.", "essentials.home"),
                NixCommandSuggestions.filterPermissionTree(NODES, "essentials.")
        );
        assertEquals(
                List.of("essentials.gamemode.creative", "essentials.gamemode.survival"),
                NixCommandSuggestions.filterPermissionTree(NODES, "essentials.gamemode.")
        );
    }

    @Test
    void appendsADotWhenTheExactSuggestionHasChildren() {
        assertTrue(
                NixCommandSuggestions.filterPermissionTree(NODES, "essentials.gamemode")
                        .contains("essentials.gamemode.")
        );
    }

    @Test
    void correctsTransposedCharactersWithDamerauDistance() {
        assertTrue(
                NixCommandSuggestions.filterPermissionTree(NODES, "essentails")
                        .contains("essentials.")
        );
    }
}
