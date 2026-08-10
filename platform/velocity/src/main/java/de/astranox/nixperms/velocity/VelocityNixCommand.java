package de.astranox.nixperms.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import de.astranox.nixperms.core.NixPermsCore;

import java.util.List;

final class VelocityNixCommand implements SimpleCommand {

    private final NixPermsCore core;

    VelocityNixCommand(NixPermsCore core) {
        if (core == null) throw new IllegalArgumentException("NixPerms core cannot be null");
        this.core = core;
    }

    @Override
    public void execute(Invocation invocation) {
        core.commands().execute(
                new VelocityCommandSender(invocation.source()), invocation.arguments()
        );
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return core.commands().suggest(
                new VelocityCommandSender(invocation.source()), invocation.arguments()
        );
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
