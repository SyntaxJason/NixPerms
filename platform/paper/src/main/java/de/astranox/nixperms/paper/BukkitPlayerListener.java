package de.astranox.nixperms.paper;

import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public final class BukkitPlayerListener implements Listener {

    private final NixPermsBukkit plugin;
    private final NixPermsCore core;
    private final PermissibleInjector injector;
    private final BukkitCommandTreeUpdater commandTreeUpdater;

    public BukkitPlayerListener(
            NixPermsBukkit plugin,
            NixPermsCore core,
            PermissibleInjector injector,
            BukkitCommandTreeUpdater commandTreeUpdater
    ) {
        this.plugin = plugin;
        this.core = core;
        this.injector = injector;
        this.commandTreeUpdater = commandTreeUpdater;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            INixUser user = core.users().loadUser(event.getUniqueId()).join();
            if (!event.getName().equals(user.name())) {
                user.edit(editor -> editor.name(event.getName())).join();
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not load permissions for " + event.getName(), error);
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "Permissions could not be loaded. Please try again."
            );
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        INixUser loaded = core.users().getUser(player.getUniqueId());
        if (loaded != null) {
            finishJoin(player, loaded);
            return;
        }
        core.users().loadUser(player.getUniqueId()).whenComplete((user, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || !player.isOnline()) {
                        if (error != null) player.kickPlayer("Permissions could not be loaded.");
                        return;
                    }
                    finishJoin(player, user);
                })
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        INixUser user = core.users().getUser(event.getPlayer().getUniqueId());
        if (user != null) updateContext(event.getPlayer(), user);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        injector.uninject(event.getPlayer());
        core.users().unloadUser(event.getPlayer().getUniqueId());
    }

    private void finishJoin(Player player, INixUser user) {
        updateContext(player, user);
        if (!injector.inject(player)) {
            player.kickPlayer("NixPerms could not attach to this player.");
            return;
        }
        commandTreeUpdater.request(player);
    }

    private void updateContext(Player player, INixUser user) {
        user.updateContext(new PermissionContext(core.serverId(), player.getWorld().getName()));
    }
}
