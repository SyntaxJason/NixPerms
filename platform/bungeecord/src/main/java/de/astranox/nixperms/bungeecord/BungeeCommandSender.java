package de.astranox.nixperms.bungeecord;

import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.platform.Platform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Locale;
import java.util.UUID;

final class BungeeCommandSender implements NixCommandSender {

    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final CommandSender handle;
    private final UUID uniqueId;
    private final String locale;

    BungeeCommandSender(CommandSender handle) {
        if (handle == null) throw new IllegalArgumentException("Command sender cannot be null");
        this.handle = handle;
        this.uniqueId = handle instanceof ProxiedPlayer player ? player.getUniqueId() : CONSOLE_UUID;
        this.locale = handle instanceof ProxiedPlayer player ? locale(player.getLocale()) : "en_us";
    }

    @Override public String name() { return handle.getName(); }
    @Override public UUID uniqueId() { return uniqueId; }
    @Override public boolean isPlayer() { return handle instanceof ProxiedPlayer; }
    @Override public boolean hasPermission(String node) { return !isPlayer() || handle.hasPermission(node); }
    @Override public String locale() { return locale; }
    @Override public Platform platform() { return isPlayer() ? Platform.BUNGEE : Platform.CONSOLE; }
    @Override
    public void sendComponent(Component component) {
        handle.sendMessage(TextComponent.fromLegacy(LEGACY.serialize(component)));
    }

    private String locale(Locale value) {
        if (value == null) return "en_us";
        return value.toLanguageTag().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
