package de.astranox.nixperms.core.command.subcommand;

import de.astranox.nixperms.api.annotation.command.Action;
import de.astranox.nixperms.api.annotation.command.Arg;
import de.astranox.nixperms.api.annotation.command.ArgType;
import de.astranox.nixperms.api.annotation.command.Subcommand;
import de.astranox.nixperms.api.annotation.command.Usage;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.core.command.NixCommandContext;
import java.util.concurrent.CompletableFuture;

@Subcommand(label = "user", aliases = {"u"})
public final class UserSubcommand {

    @Action("addperm")
    @Usage("<user> <node> [value]")
    public void addPerm(NixCommandContext ctx,
                        @Arg("user") String name,
                        @Arg(value = "node", type = ArgType.PERMISSION_NODE) String node,
                        @Arg(value = "value", def = "true", required = false) boolean value) {
        report(ctx, resolveUser(ctx, name).thenAccept(user -> {
            if (user == null) {
                ctx.reply("commands.user.not-found").with("user", name).send();
                return;
            }

            report(ctx, user.setPermission(node, value).thenRun(() ->
                    ctx.reply("commands.user.addperm.success")
                            .with("user", displayName(user))
                            .with("node", node)
                            .with("value", String.valueOf(value))
                            .send()
            ));
        }));
    }

    @Action(value = "delperm", aliases = {"removeperm"})
    @Usage("<user> <node>")
    public void delPerm(NixCommandContext ctx,
                        @Arg("user") String name,
                        @Arg(value = "node", type = ArgType.PERMISSION_NODE) String node) {
        report(ctx, resolveUser(ctx, name).thenAccept(user -> {
            if (user == null) {
                ctx.reply("commands.user.not-found").with("user", name).send();
                return;
            }

            report(ctx, user.unsetPermission(node).thenRun(() ->
                    ctx.reply("commands.user.delperm.success")
                            .with("user", displayName(user))
                            .with("node", node)
                            .send()
            ));
        }));
    }

    @Action("setgroup")
    @Usage("<user> <group>")
    public void setGroup(NixCommandContext ctx,
                         @Arg("user") String name,
                         @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", ctx.arg(3).orElse("?")).send();
            return;
        }

        report(ctx, resolveUser(ctx, name).thenAccept(user -> {
            if (user == null) {
                ctx.reply("commands.user.not-found").with("user", name).send();
                return;
            }

            user.setPrimary(group)
                    .thenRun(() -> ctx.reply("commands.user.setgroup.success")
                            .with("user", displayName(user))
                            .with("group", group.name())
                            .send())
                    .exceptionally(ex -> {
                        ctx.reply("commands.error").with("error", message(ex)).send();
                        return null;
                    });
        }));
    }

    @Action("setsecondary")
    @Usage("<user> <group>")
    public void setSecondary(NixCommandContext ctx,
                             @Arg("user") String name,
                             @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", ctx.arg(3).orElse("?")).send();
            return;
        }

        report(ctx, resolveUser(ctx, name).thenAccept(user -> {
            if (user == null) {
                ctx.reply("commands.user.not-found").with("user", name).send();
                return;
            }

            user.setSecondary(group)
                    .thenRun(() -> ctx.reply("commands.user.setsecondary.success")
                            .with("user", displayName(user))
                            .with("group", group.name())
                            .send())
                    .exceptionally(ex -> {
                        ctx.reply("commands.error").with("error", message(ex)).send();
                        return null;
                    });
        }));
    }

    @Action("clearsecondary")
    @Usage("<user>")
    public void clearSecondary(NixCommandContext ctx,
                               @Arg("user") String name) {
        report(ctx, resolveUser(ctx, name).thenAccept(user -> {
            if (user == null) {
                ctx.reply("commands.user.not-found").with("user", name).send();
                return;
            }

            user.setSecondary(null)
                    .thenRun(() -> ctx.reply("commands.user.setsecondary.success")
                            .with("user", displayName(user))
                            .with("group", "none")
                            .send())
                    .exceptionally(ex -> {
                        ctx.reply("commands.error").with("error", message(ex)).send();
                        return null;
                    });
        }));
    }

    @Action("info")
    @Usage("<user>")
    public void info(NixCommandContext ctx,
                     @Arg("user") String name) {
        report(ctx, resolveUser(ctx, name).thenAccept(user -> {
            if (user == null) {
                ctx.reply("commands.user.not-found").with("user", name).send();
                return;
            }

            ctx.reply("commands.user.info")
                    .with("user", displayName(user))
                    .with("primary", user.primary().name())
                    .with("secondary", user.secondaryEffective() != null ? user.secondaryEffective().name() : "none")
                    .send();

            user.ownPermissions().forEach((node, value) ->
                    ctx.reply("commands.group.perm-entry")
                            .with("node", node)
                            .with("value", String.valueOf(value))
                            .send()
            );
        }));
    }

    @Action("resolve")
    @Usage("<name>")
    public void resolve(NixCommandContext ctx, @Arg("name") String name) {
        report(ctx, resolveUser(ctx, name).thenAccept(user -> {
            if (user == null) {
                ctx.reply("commands.user.not-found").with("user", name).send();
                return;
            }

            ctx.reply("commands.user.info")
                    .with("user", displayName(user))
                    .with("primary", user.primary().name())
                    .with("secondary", user.secondaryEffective() != null ? user.secondaryEffective().name() : "none")
                    .send();
        }));
    }

    private CompletableFuture<INixUser> resolveUser(NixCommandContext ctx, String input) {
        return ctx.api().users().resolveUser(input);
    }

    private String displayName(INixUser user) {
        return user.name() != null ? user.name() : user.uniqueId().toString();
    }

    private String message(Throwable throwable) {
        Throwable cause = throwable.getCause();
        if (cause != null && cause.getMessage() != null) return cause.getMessage();
        if (throwable.getMessage() != null) return throwable.getMessage();
        return "unknown";
    }

    private void report(NixCommandContext context, CompletableFuture<?> operation) {
        operation.exceptionally(error -> {
            context.reply("commands.error").with("error", message(error)).send();
            return null;
        });
    }
}
