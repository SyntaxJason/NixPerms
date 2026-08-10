package de.astranox.nixperms.paper;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.NixPermsProvider;
import de.astranox.nixperms.api.permission.PermissionContext;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.NixPermsCore;
import de.astranox.nixperms.platform.common.PlatformCommandRegistrar;
import de.astranox.nixperms.platform.common.PlatformSettingsLoader;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class NixPermsBukkit extends JavaPlugin {

    private NixPermsCore core;
    private PermissibleInjector injector;
    private BukkitCommandTreeUpdater commandTreeUpdater;

    @Override
    public void onEnable() {
        try {
            core = NixPermsCore.create(PlatformSettingsLoader.load(
                    getDataFolder().toPath(), () -> getResource("config.yml")
            )).join();
            injector = new PermissibleInjector(core, getLogger());
            commandTreeUpdater = new BukkitCommandTreeUpdater(this, core);
            exposeApi();
            registerCommands();
            getServer().getPluginManager().registerEvents(
                    new BukkitPlayerListener(this, core, injector, commandTreeUpdater), this
            );
            attachOnlinePlayers();
            getLogger().info("NixPerms v" + core.version() + " enabled on server '" + core.serverId() + "'.");
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            getLogger().log(Level.SEVERE, "NixPerms could not start", cause);
            cleanup();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        cleanup();
    }

    private void exposeApi() {
        NixPermsProvider.register(core);
        getServer().getServicesManager().register(
                INixPermsAPI.class, core, this, ServicePriority.Normal
        );
    }

    private void registerCommands() {
        PlatformCommandRegistrar.register(core);
        if (PaperRuntime.registerCommands(this, core)) {
            getLogger().info("Registered /nixperms through Paper's native command API.");
            return;
        }

        registerLegacyBukkitCommand();
        getLogger().info("Registered /nixperms through the Bukkit command adapter.");
    }

    private void registerLegacyBukkitCommand() {
        PluginCommand command = getCommand("nixperms");
        if (command == null) {
            throw new IllegalStateException("Command 'nixperms' is missing from plugin.yml");
        }
        BukkitCommandExecutor executor = new BukkitCommandExecutor(this, core);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void attachOnlinePlayers() {
        List<Player> online = List.copyOf(getServer().getOnlinePlayers());
        CompletableFuture<?>[] loads = online.stream()
                .map(this::loadPlayer)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(loads).join();
        for (Player player : online) {
            core.users().getUser(player.getUniqueId()).updateContext(new PermissionContext(
                    core.serverId(), player.getWorld().getName()
            ));
            if (injector.inject(player)) commandTreeUpdater.request(player);
        }
    }


    private CompletableFuture<INixUser> loadPlayer(Player player) {
        return core.users().loadUser(player.getUniqueId()).thenCompose(user -> {
            if (player.getName().equals(user.name())) {
                return CompletableFuture.completedFuture(user);
            }
            return user.edit(editor -> editor.name(player.getName()));
        });
    }

    private void cleanup() {
        if (commandTreeUpdater != null) {
            commandTreeUpdater.close();
            commandTreeUpdater = null;
        }
        if (injector != null) {
            injector.close();
            injector = null;
        }
        if (core != null) {
            getServer().getServicesManager().unregisterAll(this);
            NixPermsProvider.unregister(core);
            core.close();
            core = null;
        }
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
