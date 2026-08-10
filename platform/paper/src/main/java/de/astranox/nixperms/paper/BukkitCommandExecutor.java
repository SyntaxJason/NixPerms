package de.astranox.nixperms.paper;

import de.astranox.nixperms.core.NixPermsCore;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public final class BukkitCommandExecutor implements CommandExecutor, TabCompleter {

    private final NixPermsBukkit plugin;
    private final NixPermsCore core;

    public BukkitCommandExecutor(NixPermsBukkit plugin, NixPermsCore core) {
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        core.commands().execute(new BukkitCommandSender(plugin, sender), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return core.commands().suggest(new BukkitCommandSender(plugin, sender), args);
    }
}
