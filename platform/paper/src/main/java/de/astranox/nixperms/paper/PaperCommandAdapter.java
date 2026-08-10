package de.astranox.nixperms.paper;

import de.astranox.nixperms.core.NixPermsCore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.Collection;

/**
 * Adapts the platform-neutral NixPerms command processor to Paper's Brigadier-backed
 * BasicCommand API without duplicating command parsing or completion logic.
 */
public final class PaperCommandAdapter implements BasicCommand {

    private static final String ROOT_PERMISSION = "nixperms.admin";

    private final NixPermsBukkit plugin;
    private final NixPermsCore core;

    public PaperCommandAdapter(NixPermsBukkit plugin, NixPermsCore core) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (core == null) throw new IllegalArgumentException("NixPerms core cannot be null");
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        core.commands().execute(new BukkitCommandSender(plugin, sender), args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        return core.commands().suggest(new BukkitCommandSender(plugin, sender), args);
    }

    @Override
    public String permission() {
        return ROOT_PERMISSION;
    }
}
