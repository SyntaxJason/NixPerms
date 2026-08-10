package de.astranox.nixperms.core.command;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.permission.PermissionRule;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

/** Aggregates known permission nodes without introducing a platform dependency into the core. */
final class PermissionNodeCatalog {

    private static final long CACHE_NANOS = 500_000_000L;

    private final INixPermsAPI api;
    private final ConcurrentSkipListSet<String> registered = new ConcurrentSkipListSet<>();
    private volatile Snapshot snapshot = new Snapshot(List.of(), 0L);

    PermissionNodeCatalog(INixPermsAPI api) {
        this.api = api;
        registered.add("nixperms.admin");
    }

    void register(String node) {
        add(registered, node);
        snapshot = new Snapshot(snapshot.nodes(), 0L);
    }

    List<String> nodes() {
        Snapshot current = snapshot;
        long now = System.nanoTime();
        if (now < current.expiresAt()) return current.nodes();
        return refresh(now);
    }

    private synchronized List<String> refresh(long now) {
        Snapshot current = snapshot;
        if (now < current.expiresAt()) return current.nodes();

        TreeSet<String> nodes = new TreeSet<>(registered);
        api.groups().loaded().forEach(group -> group.rules().stream()
                .map(PermissionRule::node)
                .forEach(node -> add(nodes, node)));
        api.users().loaded().forEach(user -> {
            user.ownRules().stream().map(PermissionRule::node).forEach(node -> add(nodes, node));
            user.permissions().effective().keySet().forEach(node -> add(nodes, node));
        });
        discoverBukkitPermissions(nodes);

        List<String> immutable = List.copyOf(nodes);
        snapshot = new Snapshot(immutable, now + CACHE_NANOS);
        return immutable;
    }

    private void discoverBukkitPermissions(Set<String> nodes) {
        try {
            ClassLoader loader = PermissionNodeCatalog.class.getClassLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, loader);
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);

            addPermissionObjects(nodes, invoke(pluginManager, "getPermissions"));
            discoverPluginDescriptions(nodes, invoke(pluginManager, "getPlugins"));

            Object server = bukkit.getMethod("getServer").invoke(null);
            Object commandMap = invoke(server, "getCommandMap");
            discoverCommandPermissions(nodes, invoke(commandMap, "getKnownCommands"));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            // This is expected on proxies and in isolated tests. Stored nodes remain available.
        }
    }

    private void discoverPluginDescriptions(Set<String> nodes, Object plugins) {
        for (Object plugin : iterable(plugins)) {
            Object description = invoke(plugin, "getDescription");
            addPermissionObjects(nodes, invoke(description, "getPermissions"));
            discoverDeclaredCommandPermissions(nodes, invoke(description, "getCommands"));
        }
    }

    private void discoverDeclaredCommandPermissions(Set<String> nodes, Object commands) {
        if (!(commands instanceof Map<?, ?> commandMap)) return;
        for (Object rawMetadata : commandMap.values()) {
            if (!(rawMetadata instanceof Map<?, ?> metadata)) continue;
            addPermissionValue(nodes, metadata.get("permission"));
        }
    }

    private void discoverCommandPermissions(Set<String> nodes, Object commands) {
        if (!(commands instanceof Map<?, ?> commandMap)) return;
        for (Object command : commandMap.values()) {
            addPermissionValue(nodes, invoke(command, "getPermission"));
        }
    }

    private void addPermissionObjects(Set<String> nodes, Object permissions) {
        for (Object permission : iterable(permissions)) {
            addPermissionValue(nodes, invoke(permission, "getName"));
            Object children = invoke(permission, "getChildren");
            if (children instanceof Map<?, ?> childMap) {
                childMap.keySet().forEach(child -> addPermissionValue(nodes, child));
            }
        }
    }

    private void addPermissionValue(Set<String> nodes, Object rawValue) {
        if (rawValue instanceof Collection<?> collection) {
            collection.forEach(value -> addPermissionValue(nodes, value));
            return;
        }
        if (rawValue == null) return;
        String value = String.valueOf(rawValue);
        for (String part : value.split(";")) add(nodes, part);
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private Collection<?> iterable(Object value) {
        if (value instanceof Collection<?> collection) return collection;
        if (value == null || !value.getClass().isArray()) return List.of();
        int length = Array.getLength(value);
        List<Object> result = new ArrayList<>(length);
        for (int index = 0; index < length; index++) result.add(Array.get(value, index));
        return result;
    }

    private void add(Collection<String> target, String node) {
        if (node == null) return;
        String normalized = node.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return;
        target.add(normalized);
    }

    private record Snapshot(List<String> nodes, long expiresAt) { }
}
