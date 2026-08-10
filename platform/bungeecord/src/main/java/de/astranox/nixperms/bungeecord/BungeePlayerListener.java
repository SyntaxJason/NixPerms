package de.astranox.nixperms.bungeecord;

import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PermissionCheckEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

final class BungeePlayerListener implements Listener {

    private final NixPermsBungee plugin;
    private final NixPermsCore core;

    BungeePlayerListener(NixPermsBungee plugin, NixPermsCore core) {
        this.plugin = plugin;
        this.core = core;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(LoginEvent event) {
        UUID uniqueId = event.getConnection().getUniqueId();
        if (uniqueId == null) {
            event.setCancelled(true);
            event.setReason(new TextComponent("Permissions could not be loaded. Please try again."));
            return;
        }

        event.registerIntent(plugin);
        core.users().loadUser(uniqueId)
                .thenCompose(user -> event.getConnection().getName().equals(user.name())
                        ? CompletableFuture.completedFuture(user)
                        : user.edit(editor -> editor.name(event.getConnection().getName())))
                .whenComplete((user, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(
                                Level.SEVERE,
                                "Could not load permissions for " + event.getConnection().getName(),
                                error
                        );
                        event.setCancelled(true);
                        event.setReason(new TextComponent(
                                "Permissions could not be loaded. Please try again."
                        ));
                    }
                    event.completeIntent(plugin);
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPermissionCheck(PermissionCheckEvent event) {
        CommandSender sender = event.getSender();
        if (!(sender instanceof ProxiedPlayer player)) return;
        INixUser user = core.users().getUser(player.getUniqueId());
        if (user == null) return;
        PermissionDecision decision = user.permissions().decision(event.getPermission());
        if (decision.isSet()) event.setHasPermission(decision.allowed());
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        INixUser user = core.users().getUser(event.getPlayer().getUniqueId());
        if (user == null) return;
        user.updateContext(PermissionContext.server(event.getServer().getInfo().getName()));
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        core.users().unloadUser(event.getPlayer().getUniqueId());
    }
}
