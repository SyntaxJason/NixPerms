package de.astranox.nixperms.api.command;

import de.astranox.nixperms.api.platform.Platform;
import net.kyori.adventure.text.Component;
import java.util.UUID;

public interface NixCommandSender {
    String name();
    UUID uniqueId();
    boolean isPlayer();
    boolean hasPermission(String node);
    String locale();
    Platform platform();
    void sendComponent(Component component);
}
