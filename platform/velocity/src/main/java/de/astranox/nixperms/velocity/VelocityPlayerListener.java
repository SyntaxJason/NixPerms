package de.astranox.nixperms.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

final class VelocityPlayerListener {

    private final NixPermsCore core;
    private final Logger logger;

    VelocityPlayerListener(NixPermsCore core, Logger logger) {
        if (core == null) throw new IllegalArgumentException("NixPerms core cannot be null");
        if (logger == null) throw new IllegalArgumentException("Logger cannot be null");
        this.core = core;
        this.logger = logger;
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public EventTask onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) return null;

        PermissionProvider fallback = event.getProvider();
        CompletableFuture<Void> load = core.users().loadUser(player.getUniqueId())
                .thenCompose(user -> player.getUsername().equals(user.name())
                        ? CompletableFuture.completedFuture(user)
                        : user.edit(editor -> editor.name(player.getUsername())))
                .thenAccept(user -> event.setProvider(subject -> {
                    PermissionFunction fallbackFunction = fallback.createFunction(subject);
                    if (!(subject instanceof Player target)) return fallbackFunction;
                    return permissionFunction(target, fallbackFunction);
                }))
                .exceptionally(error -> {
                    logger.error(
                            "Could not load permissions for {}",
                            player.getUsername(), error
                    );
                    player.disconnect(Component.text(
                            "Permissions could not be loaded. Please try again."
                    ));
                    return null;
                });
        return EventTask.resumeWhenComplete(load);
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        INixUser user = core.users().getUser(event.getPlayer().getUniqueId());
        if (user == null) return;
        event.getPlayer().getCurrentServer().ifPresent(connection ->
                user.updateContext(PermissionContext.server(
                        connection.getServerInfo().getName()
                ))
        );
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        core.users().unloadUser(event.getPlayer().getUniqueId());
    }

    private PermissionFunction permissionFunction(
            Player player,
            PermissionFunction fallback
    ) {
        return permission -> {
            INixUser user = core.users().getUser(player.getUniqueId());
            if (user == null) return fallback.getPermissionValue(permission);
            PermissionDecision decision = user.permissions().decision(permission);
            if (!decision.isSet()) return fallback.getPermissionValue(permission);
            return Tristate.fromBoolean(decision.allowed());
        };
    }
}
