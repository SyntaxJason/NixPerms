package de.astranox.nixperms.paper;

import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.platform.Platform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.Locale;

public final class BukkitCommandSender implements NixCommandSender {

    private static final UUID CONSOLE_UUID = new UUID(0, 0);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final NixPermsBukkit plugin;
    private final CommandSender handle;
    private final String name;
    private final UUID uniqueId;
    private final String locale;
    private final Platform platform;

    public BukkitCommandSender(NixPermsBukkit plugin, CommandSender handle) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (handle == null) throw new IllegalArgumentException("Command sender cannot be null");
        this.plugin = plugin;
        this.handle = handle;
        this.name = handle.getName();
        this.uniqueId = handle instanceof Player player ? player.getUniqueId() : CONSOLE_UUID;
        this.locale = handle instanceof Player player ? locale(player.getLocale()) : "en_us";
        this.platform = handle instanceof Player ? Platform.BUKKIT : Platform.CONSOLE;
    }

    @Override public String name() { return name; }
    @Override public UUID uniqueId() { return uniqueId; }
    @Override public boolean isPlayer() { return handle instanceof Player; }
    @Override public boolean hasPermission(String node) {
        if (!(handle instanceof Player)) return true;
        return handle.hasPermission(node);
    }
    @Override public String locale() { return locale; }
    @Override public Platform platform() { return platform; }

    @Override
    public void sendComponent(Component component) {
        String message = LEGACY.serialize(component);
        if (Bukkit.isPrimaryThread()) {
            handle.sendMessage(message);
            return;
        }
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> handle.sendMessage(message));
        } catch (IllegalStateException ignored) {
            // The plugin was disabled between the enabled check and scheduling.
        }
    }

    private String locale(String value) {
        if (value == null || value.isBlank()) return "en_us";
        return value.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
