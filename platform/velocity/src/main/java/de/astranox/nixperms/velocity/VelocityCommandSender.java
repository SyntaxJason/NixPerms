package de.astranox.nixperms.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.platform.Platform;
import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.UUID;

final class VelocityCommandSender implements NixCommandSender {

    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    private final CommandSource handle;
    private final String name;
    private final UUID uniqueId;
    private final String locale;

    VelocityCommandSender(CommandSource handle) {
        if (handle == null) throw new IllegalArgumentException("Command source cannot be null");
        this.handle = handle;
        if (handle instanceof Player player) {
            this.name = player.getUsername();
            this.uniqueId = player.getUniqueId();
            this.locale = locale(player.getEffectiveLocale());
            return;
        }
        this.name = "CONSOLE";
        this.uniqueId = CONSOLE_UUID;
        this.locale = "en_us";
    }

    @Override public String name() { return name; }
    @Override public UUID uniqueId() { return uniqueId; }
    @Override public boolean isPlayer() { return handle instanceof Player; }
    @Override public boolean hasPermission(String node) { return !isPlayer() || handle.hasPermission(node); }
    @Override public String locale() { return locale; }
    @Override public Platform platform() { return isPlayer() ? Platform.VELOCITY : Platform.CONSOLE; }
    @Override public void sendComponent(Component component) { handle.sendMessage(component); }

    private String locale(Locale value) {
        if (value == null) return "en_us";
        return value.toLanguageTag().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
