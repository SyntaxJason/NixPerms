package de.astranox.nixperms.core.message;

import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.message.IMessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.*;

public final class MessageService implements IMessageService {

    private static final MiniMessage MM = MiniMessage.builder()
            .editTags(tags -> tags.resolver(TagResolver.resolver("smallcaps", (args, ctx) -> {
                String text = args.popOr("smallcaps requires text argument").value();
                return Tag.selfClosingInserting(MiniMessage.miniMessage().deserialize(SmallCaps.convert(text)));
            }))).build();

    private final MessageRegistry registry;

    public MessageService(MessageRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Component resolve(NixCommandSender sender, String key, Map<String, String> placeholders) {
        String raw = registry.getRaw(sender.locale(), sender.platform(), key);
        return MM.deserialize(raw, buildResolver(placeholders, sender));
    }

    @Override
    public void send(NixCommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendComponent(resolve(sender, key, placeholders));
    }

    public MessageRegistry registry() { return registry; }

    private TagResolver buildResolver(Map<String, String> placeholders, NixCommandSender sender) {
        List<TagResolver> resolvers = new ArrayList<>();
        placeholders.forEach((key, value) -> resolvers.add(Placeholder.unparsed(key, value)));
        resolvers.add(Placeholder.unparsed("cmd", sender.platform().commandPrefix() + "nixperms"));
        return TagResolver.resolver(resolvers);
    }
}
