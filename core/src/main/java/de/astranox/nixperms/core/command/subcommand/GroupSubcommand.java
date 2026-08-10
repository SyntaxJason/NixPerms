package de.astranox.nixperms.core.command.subcommand;

import de.astranox.nixperms.api.annotation.command.Action;
import de.astranox.nixperms.api.annotation.command.Arg;
import de.astranox.nixperms.api.annotation.command.ArgType;
import de.astranox.nixperms.api.annotation.command.Subcommand;
import de.astranox.nixperms.api.annotation.command.Usage;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.core.command.NixCommandContext;

import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Subcommand(label = "group", aliases = {"g"})
public final class GroupSubcommand {

    private static final Set<String> ACTIONS = Set.of(
            "create",
            "delete",
            "addperm",
            "delperm",
            "removeperm",
            "setparent",
            "clearparent",
            "setdefaultsecondary",
            "setdefault",
            "cleardefaultsecondary",
            "cleardefault",
            "setweight",
            "addprefix",
            "addsuffix",
            "setoption",
            "info",
            "list"
    );

    @Action("create")
    @Usage("<name> [role]")
    public void create(NixCommandContext ctx,
                       @Arg("name") String name,
                       @Arg(value = "role", def = "PRIMARY", required = false) GroupRole role) {
        if (ctx.api().groups().group(name) != null) {
            ctx.reply("commands.group.already-exists").with("group", name).send();
            return;
        }

        run(ctx, ctx.api().groups().create(name, role).thenRun(() ->
                ctx.reply("commands.group.create.success")
                        .with("group", name)
                        .with("role", role.name())
                        .send()
        ));
    }

    @Action("delete")
    @Usage("<group>")
    public void delete(NixCommandContext ctx,
                       @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().delete(group).thenRun(() ->
                ctx.reply("commands.group.delete.success")
                        .with("group", group.name())
                        .send()
        ));
    }

    @Action("addperm")
    @Usage("<group> <node> [value]")
    public void addPerm(NixCommandContext ctx,
                        @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group,
                        @Arg(value = "node", type = ArgType.PERMISSION_NODE) String node,
                        @Arg(value = "value", def = "true", required = false) boolean value) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().setPermission(group, node, value).thenRun(() ->
                ctx.reply("commands.group.addperm.success")
                        .with("group", group.name())
                        .with("node", node)
                        .with("value", String.valueOf(value))
                        .send()
        ));
    }

    @Action(value = "delperm", aliases = {"removeperm"})
    @Usage("<group> <node>")
    public void delPerm(NixCommandContext ctx,
                        @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group,
                        @Arg(value = "node", type = ArgType.PERMISSION_NODE) String node) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().unsetPermission(group, node).thenRun(() ->
                ctx.reply("commands.group.delperm.success")
                        .with("group", group.name())
                        .with("node", node)
                        .send()
        ));
    }

    @Action("setparent")
    @Usage("<group> <parent>")
    public void setParent(NixCommandContext ctx,
                          @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group,
                          @Arg(value = "parent", type = ArgType.GROUP) IPermissionGroup parent) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        if (parent == null) {
            ctx.reply("commands.group.not-found").with("group", secondGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().setParent(group, parent).thenRun(() ->
                ctx.reply("commands.group.setparent.success")
                        .with("group", group.name())
                        .with("parent", parent.name())
                        .send()
        ));
    }

    @Action("clearparent")
    @Usage("<group>")
    public void clearParent(NixCommandContext ctx,
                            @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().setParent(group, null).thenRun(() ->
                ctx.reply("commands.group.setparent.success")
                        .with("group", group.name())
                        .with("parent", "none")
                        .send()
        ));
    }

    @Action(value = "setdefaultsecondary", aliases = {"setdefault"})
    @Usage("<primary> <secondary>")
    public void setDefaultSecondary(
            NixCommandContext ctx,
            @Arg(value = "primary", type = ArgType.GROUP) IPermissionGroup primary,
            @Arg(value = "secondary", type = ArgType.GROUP) IPermissionGroup secondary
    ) {
        if (primary == null || secondary == null) {
            ctx.reply("commands.group.not-found")
                    .with("group", primary == null ? firstGroupInput(ctx) : secondGroupInput(ctx))
                    .send();
            return;
        }

        run(ctx, primary.edit(editor -> editor.defaultSecondary(secondary.name())).thenRun(() ->
                ctx.reply("commands.group.setdefaultsecondary.success")
                        .with("group", primary.name())
                        .with("secondary", secondary.name())
                        .send()
        ));
    }

    @Action(value = "cleardefaultsecondary", aliases = {"cleardefault"})
    @Usage("<primary>")
    public void clearDefaultSecondary(
            NixCommandContext ctx,
            @Arg(value = "primary", type = ArgType.GROUP) IPermissionGroup primary
    ) {
        if (primary == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, primary.edit(editor -> editor.clearDefaultSecondary()).thenRun(() ->
                ctx.reply("commands.group.setdefaultsecondary.success")
                        .with("group", primary.name())
                        .with("secondary", "none")
                        .send()
        ));
    }

    @Action("setweight")
    @Usage("<group> <weight>")
    public void setWeight(NixCommandContext ctx,
                          @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group,
                          @Arg(value = "weight", type = ArgType.INT) int weight) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().setWeight(group, weight).thenRun(() ->
                ctx.reply("commands.group.setweight.success")
                        .with("group", group.name())
                        .with("weight", String.valueOf(weight))
                        .send()
        ));
    }

    @Action("addprefix")
    @Usage("<group> <priority> <prefix>")
    public void addPrefix(NixCommandContext ctx,
                          @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group,
                          @Arg(value = "priority", type = ArgType.INT) int priority,
                          @Arg("prefix") String prefix) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().addPrefix(group, priority, prefix).thenRun(() ->
                ctx.reply("commands.group.addperm.success")
                        .with("group", group.name())
                        .with("node", "prefix@" + priority)
                        .with("value", prefix)
                        .send()
        ));
    }

    @Action("addsuffix")
    @Usage("<group> <priority> <suffix>")
    public void addSuffix(NixCommandContext ctx,
                          @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group,
                          @Arg(value = "priority", type = ArgType.INT) int priority,
                          @Arg("suffix") String suffix) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().addSuffix(group, priority, suffix).thenRun(() ->
                ctx.reply("commands.group.addperm.success")
                        .with("group", group.name())
                        .with("node", "suffix@" + priority)
                        .with("value", suffix)
                        .send()
        ));
    }

    @Action("setoption")
    @Usage("<group> <key> <value>")
    public void setOption(NixCommandContext ctx,
                          @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group,
                          @Arg("key") String key,
                          @Arg("value") String value) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        run(ctx, ctx.api().groups().setOption(group, key, value).thenRun(() ->
                ctx.reply("commands.group.addperm.success")
                        .with("group", group.name())
                        .with("node", key)
                        .with("value", value)
                        .send()
        ));
    }

    @Action("info")
    @Usage("<group>")
    public void info(NixCommandContext ctx,
                     @Arg(value = "group", type = ArgType.GROUP) IPermissionGroup group) {
        if (group == null) {
            ctx.reply("commands.group.not-found").with("group", firstGroupInput(ctx)).send();
            return;
        }

        ctx.reply("commands.group.info")
                .with("group", group.name())
                .with("role", group.role().name())
                .with("weight", String.valueOf(group.weight()))
                .with("parent", group.parent().map(IPermissionGroup::name).orElse("none"))
                .with("default_secondary", group.defaultSecondary().map(IPermissionGroup::name).orElse("none"))
                .send();

        group.permissions().asMap().forEach((node, value) ->
                ctx.reply("commands.group.perm-entry")
                        .with("node", node)
                        .with("value", String.valueOf(value))
                        .send()
        );
    }

    @Action("list")
    public void list(NixCommandContext ctx) {
        ctx.api().groups().loaded().stream()
                .sorted((a, b) -> Integer.compare(b.weight(), a.weight()))
                .forEach(g -> ctx.reply("commands.group.list-entry")
                        .with("group", g.name())
                        .with("role", g.role().name())
                        .with("weight", String.valueOf(g.weight()))
                        .send());
    }

    private String firstGroupInput(NixCommandContext ctx) {
        String first = ctx.arg(1).orElse("?");
        if (!ACTIONS.contains(first.toLowerCase(Locale.ROOT))) return first;
        return ctx.arg(2).orElse("?");
    }

    private String secondGroupInput(NixCommandContext ctx) {
        return ctx.arg(3).orElse("?");
    }

    private void run(NixCommandContext context, CompletableFuture<?> operation) {
        operation.exceptionally(error -> {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            context.reply("commands.error").with("error", message).send();
            return null;
        });
    }
}
