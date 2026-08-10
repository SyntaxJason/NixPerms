package de.astranox.nixperms.api.command;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.message.ReplyBuilder;
import de.astranox.nixperms.api.platform.Platform;
import net.kyori.adventure.text.Component;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface ICommandContext {
    NixCommandSender sender();
    String[] args();
    INixPermsAPI api();
    Platform platform();
    Optional<String> arg(int index);
    <T> Optional<T> arg(int index, Function<String, T> mapper);
    ReplyBuilder reply(String messageKey);
    void sendRaw(Component component);
}
