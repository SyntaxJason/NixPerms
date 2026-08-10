package de.astranox.nixperms.core.command;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.annotation.command.Action;
import de.astranox.nixperms.api.annotation.command.Arg;
import de.astranox.nixperms.api.annotation.command.ArgType;
import de.astranox.nixperms.api.annotation.command.Subcommand;
import de.astranox.nixperms.api.annotation.command.Usage;
import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.user.INixUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RegisteredSubcommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisteredSubcommand.class);

    private final Object instance;
    private final Subcommand annotation;
    private final INixPermsAPI api;
    private final PermissionNodeCatalog permissionCatalog;
    private final ConcurrentHashMap<String, Method> actions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> aliases = new ConcurrentHashMap<>();

    RegisteredSubcommand(
            Object instance,
            Subcommand annotation,
            INixPermsAPI api,
            PermissionNodeCatalog permissionCatalog
    ) {
        this.instance = instance;
        this.annotation = annotation;
        this.api = api;
        this.permissionCatalog = permissionCatalog;

        for (Method method : instance.getClass().getDeclaredMethods()) {
            Action action = method.getAnnotation(Action.class);
            if (action == null) continue;
            actions.put(action.value().toLowerCase(Locale.ROOT), method);
            for (String alias : action.aliases()) aliases.put(alias.toLowerCase(Locale.ROOT), method);
        }
    }

    void execute(NixCommandSender sender, String[] args) {
        if (!sender.hasPermission(annotation.permission())) {
            api.messages().send(sender, "commands.no-permission", Map.of());
            return;
        }
        if (args.length < 2) {
            api.messages().send(sender, "commands." + annotation.label() + ".usage", Map.of());
            return;
        }

        ResolvedAction resolvedAction = resolveAction(args);
        if (resolvedAction == null) {
            api.messages().send(sender, "commands.unknown-action", Map.of(
                    "input", args.length > 1 ? args[1] : "?"
            ));
            return;
        }

        NixCommandContext context = new NixCommandContext(sender, args, api);
        Object[] resolvedArgs = resolveArgs(
                resolvedAction.method(), context, args, resolvedAction.actionIndex()
        );
        if (resolvedArgs == null) {
            Usage usage = resolvedAction.method().getAnnotation(Usage.class);
            api.messages().send(sender, "commands.action-usage", Map.of(
                    "command", commandPath(resolvedAction),
                    "arguments", usageArguments(usage)
            ));
            return;
        }

        try {
            resolvedAction.method().setAccessible(true);
            resolvedAction.method().invoke(instance, resolvedArgs);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            api.messages().send(sender, "commands.error", Map.of(
                    "error", cause.getMessage() != null ? cause.getMessage() : "unknown"
            ));
            LOGGER.warn("NixPerms command failed", cause);
        } catch (Exception exception) {
            api.messages().send(sender, "commands.error", Map.of(
                    "error", exception.getMessage() != null ? exception.getMessage() : "unknown"
            ));
            LOGGER.warn("NixPerms command dispatch failed", exception);
        }
    }

    List<String> suggest(NixCommandSender sender, String[] args) {
        if (!sender.hasPermission(annotation.permission()) || args.length <= 1) return List.of();
        if (args.length == 2) return NixCommandSuggestions.filter(actions.keySet(), args[1]);
        if (args.length == 3 && !isAction(args[1])) {
            return NixCommandSuggestions.filter(actions.keySet(), args[2]);
        }

        ResolvedAction resolvedAction = resolveAction(args);
        if (resolvedAction == null) return NixCommandSuggestions.filter(actions.keySet(), args[1]);
        return suggestArg(resolvedAction, args, sender);
    }

    private String commandPath(ResolvedAction action) {
        return "/nixperms " + annotation.label() + " " + action.actionName();
    }

    private String usageArguments(Usage usage) {
        return usage == null ? "" : usage.value();
    }

    private ResolvedAction resolveAction(String[] args) {
        Method methodAtOne = findMethod(args[1]);
        if (methodAtOne != null) {
            return new ResolvedAction(actionName(methodAtOne), methodAtOne, 1);
        }
        if (args.length < 3) return null;
        Method methodAtTwo = findMethod(args[2]);
        if (methodAtTwo == null) return null;
        return new ResolvedAction(actionName(methodAtTwo), methodAtTwo, 2);
    }

    private String actionName(Method method) {
        return method.getAnnotation(Action.class).value().toLowerCase(Locale.ROOT);
    }

    private Method findMethod(String input) {
        String key = input.toLowerCase(Locale.ROOT);
        Method method = actions.get(key);
        return method != null ? method : aliases.get(key);
    }

    private boolean isAction(String input) {
        return findMethod(input) != null;
    }

    private Object[] resolveArgs(Method method, NixCommandContext context, String[] rawArgs, int actionIndex) {
        Parameter[] parameters = method.getParameters();
        Object[] resolved = new Object[parameters.length];
        int logicalPosition = 0;
        int argumentCount = (int) Arrays.stream(parameters)
                .filter(parameter -> parameter.isAnnotationPresent(Arg.class))
                .count();

        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            if (NixCommandContext.class.isAssignableFrom(parameter.getType())) {
                resolved[index] = context;
                continue;
            }

            Arg argument = parameter.getAnnotation(Arg.class);
            if (argument == null) return null;
            int rawIndex = mapRawIndex(actionIndex, logicalPosition++);
            String raw = rawIndex < rawArgs.length ? rawArgs[rawIndex] : null;
            if (raw != null && logicalPosition == argumentCount && trailingString(parameter, argument)) {
                raw = String.join(" ", Arrays.copyOfRange(rawArgs, rawIndex, rawArgs.length));
            }
            if (raw == null && argument.required() && argument.def().isEmpty()) return null;

            String value = raw != null ? raw : argument.def();
            Object resolvedValue = resolveArgValue(parameter.getType(), argument, value, context);
            if (resolvedValue == null && argument.required()) return null;
            resolved[index] = resolvedValue;
        }
        return resolved;
    }

    private int mapRawIndex(int actionIndex, int logicalPosition) {
        if (actionIndex == 1) return 2 + logicalPosition;
        if (logicalPosition == 0) return 1;
        return 2 + logicalPosition;
    }

    private Object resolveArgValue(Class<?> type, Arg annotation, String value, NixCommandContext context) {
        ArgType argType = annotation.type() == ArgType.AUTO ? detectType(type) : annotation.type();
        return switch (argType) {
            case STRING, PERMISSION_NODE -> value;
            case INT -> integer(value);
            case BOOLEAN -> bool(value);
            case DOUBLE -> decimal(value);
            case GROUP -> context.api().groups().group(value);
            case USER -> loadedUser(value);
            case GROUP_ROLE -> groupRole(value);
            default -> value;
        };
    }

    private Integer integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean bool(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return null;
    }

    private Double decimal(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private GroupRole groupRole(String value) {
        try {
            return GroupRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ArgType detectType(Class<?> type) {
        if (type == String.class) return ArgType.STRING;
        if (type == int.class || type == Integer.class) return ArgType.INT;
        if (type == boolean.class || type == Boolean.class) return ArgType.BOOLEAN;
        if (type == double.class || type == Double.class) return ArgType.DOUBLE;
        if (IPermissionGroup.class.isAssignableFrom(type)) return ArgType.GROUP;
        if (INixUser.class.isAssignableFrom(type)) return ArgType.USER;
        if (GroupRole.class.isAssignableFrom(type)) return ArgType.GROUP_ROLE;
        return ArgType.STRING;
    }

    private boolean trailingString(Parameter parameter, Arg annotation) {
        ArgType type = annotation.type() == ArgType.AUTO ? detectType(parameter.getType()) : annotation.type();
        return type == ArgType.STRING;
    }

    private List<String> suggestArg(ResolvedAction action, String[] args, NixCommandSender sender) {
        Parameter[] parameters = action.method().getParameters();
        int logicalPosition = 0;
        for (Parameter parameter : parameters) {
            if (NixCommandContext.class.isAssignableFrom(parameter.getType())) continue;
            Arg argument = parameter.getAnnotation(Arg.class);
            if (argument == null) continue;

            int rawIndex = mapRawIndex(action.actionIndex(), logicalPosition++);
            if (rawIndex != args.length - 1) continue;
            ArgType type = suggestionType(parameter, argument);
            String input = args[rawIndex];

            return switch (type) {
                case GROUP -> suggestGroups(action, argument, input);
                case USER -> NixCommandSuggestions.filter(userNames(sender), input);
                case GROUP_ROLE -> NixCommandSuggestions.filter(List.of("PRIMARY", "SECONDARY"), input);
                case BOOLEAN -> NixCommandSuggestions.filter(List.of("true", "false"), input);
                case PERMISSION_NODE -> NixCommandSuggestions.filterPermissionTree(
                        permissionCandidates(action, args), input
                );
                default -> List.of();
            };
        }
        return List.of();
    }

    private ArgType suggestionType(Parameter parameter, Arg argument) {
        if (argument.type() != ArgType.AUTO) return argument.type();
        if ("user".equalsIgnoreCase(argument.value()) && annotation.label().equalsIgnoreCase("user")) {
            return ArgType.USER;
        }
        return detectType(parameter.getType());
    }

    private List<String> suggestGroups(ResolvedAction action, Arg argument, String input) {
        Collection<IPermissionGroup> groups = api.groups().loaded();
        String argumentName = argument.value().toLowerCase(Locale.ROOT);
        GroupRole role = null;
        if (argumentName.contains("secondary") || action.actionName().equals("setsecondary")) {
            role = GroupRole.SECONDARY;
        }
        if (argumentName.contains("primary") || action.actionName().equals("setgroup")) {
            role = GroupRole.PRIMARY;
        }
        GroupRole expected = role;
        return NixCommandSuggestions.filter(
                groups.stream()
                        .filter(group -> expected == null || group.role() == expected)
                        .map(IPermissionGroup::name)
                        .toList(),
                input
        );
    }

    private Collection<String> permissionCandidates(ResolvedAction action, String[] args) {
        if (!action.actionName().equals("delperm")) return permissionCatalog.nodes();
        int ownerIndex = mapRawIndex(action.actionIndex(), 0);
        if (ownerIndex >= args.length) return permissionCatalog.nodes();

        String owner = args[ownerIndex];
        if (annotation.label().equalsIgnoreCase("group")) {
            IPermissionGroup group = api.groups().group(owner);
            if (group != null) return group.rules().stream().map(PermissionRule::node).toList();
        }
        if (annotation.label().equalsIgnoreCase("user")) {
            INixUser user = loadedUser(owner);
            if (user != null) return user.ownRules().stream().map(PermissionRule::node).toList();
        }
        return permissionCatalog.nodes();
    }

    private List<String> userNames(NixCommandSender sender) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        api.users().loaded().forEach(user -> {
            if (user.name() != null && !user.name().isBlank()) names.add(user.name());
        });
        if (sender.isPlayer() && sender.name() != null && !sender.name().isBlank()) {
            names.add(sender.name());
        }
        discoverBukkitUserNames(names);
        discoverBungeeUserNames(names);
        return List.copyOf(names);
    }

    private void discoverBukkitUserNames(Collection<String> names) {
        try {
            Class<?> bukkit = Class.forName(
                    "org.bukkit.Bukkit", false, RegisteredSubcommand.class.getClassLoader()
            );
            Object onlinePlayers = bukkit.getMethod("getOnlinePlayers").invoke(null);
            addNames(names, onlinePlayers, "getName");
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            // Not running on Bukkit/Paper.
        }
    }

    private void discoverBungeeUserNames(Collection<String> names) {
        try {
            Class<?> proxyType = Class.forName(
                    "net.md_5.bungee.api.ProxyServer", false, RegisteredSubcommand.class.getClassLoader()
            );
            Object proxy = proxyType.getMethod("getInstance").invoke(null);
            Object onlinePlayers = proxyType.getMethod("getPlayers").invoke(proxy);
            addNames(names, onlinePlayers, "getName");
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            // Not running on BungeeCord.
        }
    }

    private void addNames(Collection<String> names, Object players, String accessor) {
        if (!(players instanceof Iterable<?> iterable)) return;
        for (Object player : iterable) {
            try {
                Object name = player.getClass().getMethod(accessor).invoke(player);
                if (name != null && !String.valueOf(name).isBlank()) names.add(String.valueOf(name));
            } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
                // Ignore one incompatible platform object and continue with the remaining players.
            }
        }
    }

    private INixUser loadedUser(String value) {
        UUID uuid = uuidOrNull(value);
        if (uuid != null) return api.users().getUser(uuid);
        return api.users().loaded().stream()
                .filter(user -> user.name() != null)
                .filter(user -> user.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }

    private UUID uuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ResolvedAction(String actionName, Method method, int actionIndex) { }
}
