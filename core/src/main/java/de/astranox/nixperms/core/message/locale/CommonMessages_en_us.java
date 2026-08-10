package de.astranox.nixperms.core.message.locale;

import de.astranox.nixperms.api.annotation.message.*;
import de.astranox.nixperms.api.message.MessageProvider;
import de.astranox.nixperms.core.message.NixPermsStyle;

@Locale("en_us")
public final class CommonMessages_en_us {

    @Message("commands.usage") public static MessageProvider usage = () -> NixPermsStyle.WHITE + "<bold>Usage</bold> " + NixPermsStyle.MUTED + "› " + NixPermsStyle.PRIMARY + "/nixperms " + NixPermsStyle.WHITE + "\\<user|group|reload>";
    @Message("commands.no-permission") public static MessageProvider noPermission = () -> NixPermsStyle.ERROR + "You don't have permission to do that.";
    @Message("commands.unknown") public static MessageProvider unknown = () -> NixPermsStyle.ERROR + "Unknown command: " + NixPermsStyle.PRIMARY + "<input>";
    @Message("commands.unknown-did-you-mean") public static MessageProvider didYouMean = () -> NixPermsStyle.ERROR + "Unknown command: " + NixPermsStyle.PRIMARY + "<input>" + NixPermsStyle.MUTED + " — did you mean " + NixPermsStyle.WHITE + "<suggestion>" + NixPermsStyle.MUTED + "?";
    @Message("commands.unknown-action") public static MessageProvider unknownAction = () -> NixPermsStyle.ERROR + "Unknown action: " + NixPermsStyle.PRIMARY + "<input>";
    @Message("commands.action-usage") public static MessageProvider actionUsage = () -> NixPermsStyle.WHITE + "<bold>Usage</bold> " + NixPermsStyle.MUTED + "› " + NixPermsStyle.PRIMARY + "<command> " + NixPermsStyle.WHITE + "<arguments>";
    @Message("commands.error") public static MessageProvider error = () -> NixPermsStyle.ERROR + "An error occurred: " + NixPermsStyle.MUTED + "<error>";
    @Message("commands.reload.usage") public static MessageProvider reloadUsage = () -> NixPermsStyle.WHITE + "<bold>Usage</bold> " + NixPermsStyle.MUTED + "› " + NixPermsStyle.PRIMARY + "/nixperms reload " + NixPermsStyle.WHITE + "\\<data|all>";
    @Message("commands.reload.success") public static MessageProvider reloadSuccess = () -> NixPermsStyle.SUCCESS + "NixPerms reloaded successfully.";
}
