package de.astranox.nixperms.paper;

import de.astranox.nixperms.core.NixPermsCore;

import java.util.List;

/** Registers NixPerms through Paper's native Brigadier-backed command bridge. */
public final class PaperCommandRegistrar {

    private PaperCommandRegistrar() { }

    public static void register(NixPermsBukkit plugin, NixPermsCore core) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (core == null) throw new IllegalArgumentException("NixPerms core cannot be null");

        plugin.registerCommand(
                "nixperms",
                "Manage NixPerms users and groups",
                List.of("nixp"),
                new PaperCommandAdapter(plugin, core)
        );
    }
}
