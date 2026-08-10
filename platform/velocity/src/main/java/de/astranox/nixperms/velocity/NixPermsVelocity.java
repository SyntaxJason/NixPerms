package de.astranox.nixperms.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.astranox.nixperms.api.NixPermsProvider;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import de.astranox.nixperms.platform.common.PlatformCommandRegistrar;
import de.astranox.nixperms.platform.common.PlatformSettingsLoader;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Plugin(
        id = "nixperms",
        name = "NixPerms",
        version = NixPermsCore.VERSION,
        description = "Fast, predictable permissions for modern Minecraft networks.",
        authors = {"Astranox"}
)
public final class NixPermsVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private NixPermsCore core;
    private CommandMeta commandMeta;
    private VelocityPlayerListener playerListener;

    @Inject
    public NixPermsVelocity(
            ProxyServer server,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        if (server == null) throw new IllegalArgumentException("Proxy server cannot be null");
        if (logger == null) throw new IllegalArgumentException("Logger cannot be null");
        if (dataDirectory == null) throw new IllegalArgumentException("Data directory cannot be null");
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            core = NixPermsCore.create(PlatformSettingsLoader.load(
                    dataDirectory, this::defaultConfig
            )).join();
            NixPermsProvider.register(core);
            PlatformCommandRegistrar.register(core);
            registerCommand();
            playerListener = new VelocityPlayerListener(core, logger);
            server.getEventManager().register(this, playerListener);
            attachOnlinePlayers();
            logger.info(
                    "NixPerms v{} enabled on Velocity node '{}'.",
                    core.version(), core.serverId()
            );
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            logger.error("NixPerms could not start", cause);
            cleanup();
            throw new IllegalStateException("NixPerms could not start", cause);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        cleanup();
    }

    private InputStream defaultConfig() {
        return getClass().getClassLoader().getResourceAsStream("config.yml");
    }

    private void registerCommand() {
        commandMeta = server.getCommandManager().metaBuilder("nixperms")
                .aliases("nixp")
                .plugin(this)
                .build();
        server.getCommandManager().register(commandMeta, new VelocityNixCommand(core));
    }

    private void attachOnlinePlayers() {
        List<Player> players = List.copyOf(server.getAllPlayers());
        CompletableFuture<?>[] loads = players.stream()
                .map(this::loadPlayer)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(loads).join();
    }

    private CompletableFuture<INixUser> loadPlayer(Player player) {
        return core.users().loadUser(player.getUniqueId()).thenCompose(user -> {
            user.updateContext(context(player));
            if (player.getUsername().equals(user.name())) {
                return CompletableFuture.completedFuture(user);
            }
            return user.edit(editor -> editor.name(player.getUsername()));
        });
    }

    private PermissionContext context(Player player) {
        return player.getCurrentServer()
                .map(connection -> PermissionContext.server(
                        connection.getServerInfo().getName()
                ))
                .orElseGet(() -> PermissionContext.server(core.serverId()));
    }

    private void cleanup() {
        if (commandMeta != null) {
            server.getCommandManager().unregister(commandMeta);
            commandMeta = null;
        }
        if (playerListener != null) {
            server.getEventManager().unregisterListener(this, playerListener);
            playerListener = null;
        }
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
