package de.astranox.nixperms.bungeecord;

import de.astranox.nixperms.api.NixPermsProvider;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import de.astranox.nixperms.platform.common.PlatformCommandRegistrar;
import de.astranox.nixperms.platform.common.PlatformSettingsLoader;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class NixPermsBungee extends Plugin {

    private NixPermsCore core;

    @Override
    public void onEnable() {
        try {
            core = NixPermsCore.create(PlatformSettingsLoader.load(
                    getDataFolder().toPath(), () -> getResourceAsStream("config.yml")
            )).join();
            NixPermsProvider.register(core);
            PlatformCommandRegistrar.register(core);
            getProxy().getPluginManager().registerListener(this, new BungeePlayerListener(this, core));
            getProxy().getPluginManager().registerCommand(this, new BungeeNixCommand(core));
            attachOnlinePlayers();
            getLogger().info("NixPerms v" + core.version() +
                    " enabled on BungeeCord node '" + core.serverId() + "'.");
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            getLogger().log(Level.SEVERE, "NixPerms could not start", cause);
            cleanup();
            throw new IllegalStateException("NixPerms could not start", cause);
        }
    }

    @Override
    public void onDisable() {
        cleanup();
    }

    private void attachOnlinePlayers() {
        List<ProxiedPlayer> players = List.copyOf(getProxy().getPlayers());
        CompletableFuture<?>[] loads = players.stream()
                .map(this::loadPlayer)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(loads).join();
    }

    private CompletableFuture<INixUser> loadPlayer(ProxiedPlayer player) {
        return core.users().loadUser(player.getUniqueId()).thenCompose(user -> {
            user.updateContext(context(player));
            if (player.getName().equals(user.name())) {
                return CompletableFuture.completedFuture(user);
            }
            return user.edit(editor -> editor.name(player.getName()));
        });
    }

    private PermissionContext context(ProxiedPlayer player) {
        if (player.getServer() == null) return PermissionContext.server(core.serverId());
        return PermissionContext.server(player.getServer().getInfo().getName());
    }

    private void cleanup() {
        getProxy().getPluginManager().unregisterCommands(this);
        getProxy().getPluginManager().unregisterListeners(this);
        if (core == null) return;
        NixPermsProvider.unregister(core);
        core.close();
        core = null;
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null &&
                (current instanceof java.util.concurrent.CompletionException ||
                        current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }
}
