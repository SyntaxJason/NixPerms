package de.astranox.nixperms.platform.common;

import de.astranox.nixperms.core.NixPermsCore;
import de.astranox.nixperms.core.command.subcommand.GroupSubcommand;
import de.astranox.nixperms.core.command.subcommand.ReloadSubcommand;
import de.astranox.nixperms.core.command.subcommand.UserSubcommand;

public final class PlatformCommandRegistrar {

    private PlatformCommandRegistrar() { }

    public static void register(NixPermsCore core) {
        if (core == null) throw new IllegalArgumentException("NixPerms core cannot be null");
        core.commands().register(new GroupSubcommand());
        core.commands().register(new UserSubcommand());
        core.commands().register(new ReloadSubcommand(core));
    }
}
