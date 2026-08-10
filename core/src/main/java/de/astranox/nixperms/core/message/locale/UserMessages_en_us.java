package de.astranox.nixperms.core.message.locale;

import de.astranox.nixperms.api.annotation.message.*;
import de.astranox.nixperms.api.message.MessageProvider;
import de.astranox.nixperms.core.message.NixPermsStyle;

@Locale("en_us")
public final class UserMessages_en_us {

    @Message("commands.user.usage") public static MessageProvider usage = () -> NixPermsStyle.WHITE + "<bold>Usage</bold> " + NixPermsStyle.MUTED + "› " + NixPermsStyle.PRIMARY + "/nixperms user " + NixPermsStyle.WHITE + "\\<action>";
    @Message("commands.user.not-found") public static MessageProvider notFound = () -> NixPermsStyle.ERROR + "User " + NixPermsStyle.PRIMARY + "<user>" + NixPermsStyle.WHITE + " not found.";
    @Message("commands.user.addperm.success") public static MessageProvider addPermSuccess = () -> NixPermsStyle.SUCCESS + "Set " + NixPermsStyle.PRIMARY + "<node>" + NixPermsStyle.MUTED + " → " + NixPermsStyle.PRIMARY + "<value>" + NixPermsStyle.WHITE + " on user " + NixPermsStyle.PRIMARY + "<user>";
    @Message("commands.user.delperm.success") public static MessageProvider delPermSuccess = () -> NixPermsStyle.SUCCESS + "Removed " + NixPermsStyle.PRIMARY + "<node>" + NixPermsStyle.WHITE + " from user " + NixPermsStyle.PRIMARY + "<user>";
    @Message("commands.user.setgroup.success") public static MessageProvider setGroupSuccess = () -> NixPermsStyle.SUCCESS + "Set primary group of " + NixPermsStyle.PRIMARY + "<user>" + NixPermsStyle.WHITE + " to " + NixPermsStyle.PRIMARY + "<group>";
    @Message("commands.user.setsecondary.success") public static MessageProvider setSecondarySuccess = () -> NixPermsStyle.SUCCESS + "Set secondary group of " + NixPermsStyle.PRIMARY + "<user>" + NixPermsStyle.WHITE + " to " + NixPermsStyle.PRIMARY + "<group>";
    @Message("commands.user.info") public static MessageProvider info = () -> NixPermsStyle.PRIMARY + "<user> " + NixPermsStyle.MUTED + "| Primary: <white><primary> " + NixPermsStyle.MUTED + "| Secondary: <white><secondary>";
}
