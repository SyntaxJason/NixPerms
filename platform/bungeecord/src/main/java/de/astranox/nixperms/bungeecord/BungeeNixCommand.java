package de.astranox.nixperms.bungeecord;

import de.astranox.nixperms.core.NixPermsCore;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

final class BungeeNixCommand extends Command implements TabExecutor {

    private final NixPermsCore core;

    BungeeNixCommand(NixPermsCore core) {
        super("nixperms", null, "nixp");
        if (core == null) throw new IllegalArgumentException("NixPerms core cannot be null");
        this.core = core;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        core.commands().execute(new BungeeCommandSender(sender), args);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return core.commands().suggest(new BungeeCommandSender(sender), args);
    }
}
