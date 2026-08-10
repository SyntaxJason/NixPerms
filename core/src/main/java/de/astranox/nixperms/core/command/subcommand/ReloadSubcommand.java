package de.astranox.nixperms.core.command.subcommand;

import de.astranox.nixperms.api.annotation.command.Action;
import de.astranox.nixperms.api.annotation.command.Subcommand;
import de.astranox.nixperms.core.NixPermsCore;
import de.astranox.nixperms.core.command.NixCommandContext;

@Subcommand(label = "reload", aliases = {"rl"})
public final class ReloadSubcommand {

    private final NixPermsCore core;

    public ReloadSubcommand(NixPermsCore core) {
        this.core = core;
    }

    @Action("data")
    public void reloadData(NixCommandContext context) {
        core.reloadData()
                .thenRun(() -> context.reply("commands.reload.success").send())
                .exceptionally(error -> {
                    context.reply("commands.error").with("error", message(error)).send();
                    return null;
                });
    }

    @Action("all")
    public void reloadAll(NixCommandContext context) {
        reloadData(context);
    }

    private String message(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
