package de.astranox.nixperms.api.message;

import de.astranox.nixperms.api.command.NixCommandSender;
import net.kyori.adventure.text.Component;
import java.util.Map;

public interface IMessageService {
    Component resolve(NixCommandSender sender, String key, Map<String, String> placeholders);
    void send(NixCommandSender sender, String key, Map<String, String> placeholders);
}
