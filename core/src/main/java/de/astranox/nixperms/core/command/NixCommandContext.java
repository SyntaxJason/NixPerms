package de.astranox.nixperms.core.command;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.command.ICommandContext;
import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.message.ReplyBuilder;
import de.astranox.nixperms.api.platform.Platform;
import java.util.Optional;
import java.util.function.Function;

public final class NixCommandContext implements ICommandContext {

    private final NixCommandSender sender;
    private final String[] args;
    private final INixPermsAPI api;

    public NixCommandContext(NixCommandSender sender, String[] args, INixPermsAPI api) {
        this.sender = sender;
        this.args = args;
        this.api = api;
    }

    @Override public NixCommandSender sender() { return sender; }
    @Override public String[] args() { return args; }
    @Override public INixPermsAPI api() { return api; }
    @Override public Platform platform() { return sender.platform(); }

    @Override
    public Optional<String> arg(int index) {
        if (index >= args.length) return Optional.empty();
        return Optional.of(args[index]);
    }

    @Override
    public <T> Optional<T> arg(int index, Function<String, T> mapper) {
        return arg(index).flatMap(value -> {
            try { return Optional.of(mapper.apply(value)); }
            catch (Exception ignored) { return Optional.empty(); }
        });
    }

    @Override
    public ReplyBuilder reply(String messageKey) {
        return new NixReplyBuilder(messageKey, sender, api);
    }

    @Override
    public void sendRaw(net.kyori.adventure.text.Component component) {
        sender.sendComponent(component);
    }
}
