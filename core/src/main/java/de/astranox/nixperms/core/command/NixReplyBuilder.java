package de.astranox.nixperms.core.command;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.message.ReplyBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

final class NixReplyBuilder implements ReplyBuilder {

    private final String messageKey;
    private final NixCommandSender sender;
    private final INixPermsAPI api;
    private final Map<String, String> placeholders = new LinkedHashMap<>();

    NixReplyBuilder(String messageKey, NixCommandSender sender, INixPermsAPI api) {
        this.messageKey = messageKey;
        this.sender = sender;
        this.api = api;
    }

    @Override
    public ReplyBuilder with(String placeholder, Object value) {
        placeholders.put(placeholder, String.valueOf(value));
        return this;
    }

    @Override
    public void send() {
        api.messages().send(sender, messageKey, placeholders);
    }
}
