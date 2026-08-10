package de.astranox.nixperms.core.message.locale;

import de.astranox.nixperms.api.annotation.message.*;
import de.astranox.nixperms.api.message.MessageProvider;
import de.astranox.nixperms.core.message.NixPermsStyle;

@Locale("en_us")
@MessagePrefix(value = NixPermsStyle.PREFIX, global = true)
public final class GroupMessages_en_us {

    @Message("commands.group.usage") public static MessageProvider usage = () -> NixPermsStyle.WHITE + "<bold>Usage</bold> " + NixPermsStyle.MUTED + "› " + NixPermsStyle.PRIMARY + "/nixperms group " + NixPermsStyle.WHITE + "\\<action>";
    @Message("commands.group.create.success") public static MessageProvider createSuccess = () -> NixPermsStyle.SUCCESS + "Group " + NixPermsStyle.PRIMARY + "<group>" + NixPermsStyle.WHITE + " created with role " + NixPermsStyle.PRIMARY + "<role>";
    @Message("commands.group.delete.success") public static MessageProvider deleteSuccess = () -> NixPermsStyle.SUCCESS + "Group " + NixPermsStyle.PRIMARY + "<group>" + NixPermsStyle.WHITE + " deleted.";
    @Message("commands.group.not-found") public static MessageProvider notFound = () -> NixPermsStyle.ERROR + "Group " + NixPermsStyle.PRIMARY + "<group>" + NixPermsStyle.WHITE + " not found.";
    @Message("commands.group.already-exists") public static MessageProvider alreadyExists = () -> NixPermsStyle.ERROR + "Group " + NixPermsStyle.PRIMARY + "<group>" + NixPermsStyle.WHITE + " already exists.";
    @Message("commands.group.addperm.success") public static MessageProvider addPermSuccess = () -> NixPermsStyle.SUCCESS + "Set " + NixPermsStyle.PRIMARY + "<node>" + NixPermsStyle.MUTED + " → " + NixPermsStyle.PRIMARY + "<value>" + NixPermsStyle.WHITE + " on group " + NixPermsStyle.PRIMARY + "<group>";
    @Message("commands.group.delperm.success") public static MessageProvider delPermSuccess = () -> NixPermsStyle.SUCCESS + "Removed " + NixPermsStyle.PRIMARY + "<node>" + NixPermsStyle.WHITE + " from group " + NixPermsStyle.PRIMARY + "<group>";
    @Message("commands.group.setparent.success") public static MessageProvider setParentSuccess = () -> NixPermsStyle.SUCCESS + "Set parent of " + NixPermsStyle.PRIMARY + "<group>" + NixPermsStyle.WHITE + " to " + NixPermsStyle.PRIMARY + "<parent>";
    @Message("commands.group.setdefaultsecondary.success") public static MessageProvider setDefaultSecondarySuccess = () -> NixPermsStyle.SUCCESS + "Set default secondary of " + NixPermsStyle.PRIMARY + "<group>" + NixPermsStyle.WHITE + " to " + NixPermsStyle.PRIMARY + "<secondary>";
    @Message("commands.group.setweight.success") public static MessageProvider setWeightSuccess = () -> NixPermsStyle.SUCCESS + "Set weight of " + NixPermsStyle.PRIMARY + "<group>" + NixPermsStyle.WHITE + " to " + NixPermsStyle.PRIMARY + "<weight>";
    @Message("commands.group.info") public static MessageProvider info = () -> NixPermsStyle.PRIMARY + "<group> " + NixPermsStyle.MUTED + "| Role: <white><role> " + NixPermsStyle.MUTED + "| Weight: <white><weight> " + NixPermsStyle.MUTED + "| Parent: <white><parent> " + NixPermsStyle.MUTED + "| Default secondary: <white><default_secondary>";
    @Message("commands.group.perm-entry") public static MessageProvider permissionEntry = () -> NixPermsStyle.MUTED + "- <white><node> " + NixPermsStyle.MUTED + "= <white><value>";
    @Message("commands.group.list-entry") public static MessageProvider listEntry = () -> NixPermsStyle.PRIMARY + "<group> " + NixPermsStyle.MUTED + "| <role> | weight <white><weight>";
}
