package de.astranox.nixperms.paper;

import de.astranox.nixperms.core.NixPermsCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PermissibleInjector implements AutoCloseable {

    private final NixPermsCore core;
    private final Logger logger;
    private final Map<UUID, Permissible> originals = new ConcurrentHashMap<>();
    private volatile Field permissibleField;

    public PermissibleInjector(NixPermsCore core, Logger logger) {
        this.core = core;
        this.logger = logger;
    }

    public boolean inject(Player player) {
        requireMainThread();
        if (originals.containsKey(player.getUniqueId())) return true;
        try {
            Field field = field(player);
            Object current = field.get(player);
            if (!(current instanceof Permissible original)) {
                throw new IllegalStateException("Current player permissible has an unexpected type");
            }
            if (current instanceof BukkitPermissibleBase) return true;
            field.set(player, new BukkitPermissibleBase(player, core));
            originals.put(player.getUniqueId(), original);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            logger.log(Level.SEVERE, "Could not inject permissions for " + player.getName(), error);
            return false;
        }
    }

    public void uninject(Player player) {
        requireMainThread();
        Permissible original = originals.remove(player.getUniqueId());
        if (original == null) return;
        try {
            field(player).set(player, original);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logger.log(Level.WARNING, "Could not restore permissions for " + player.getName(), error);
        }
    }

    @Override
    public void close() {
        requireMainThread();
        for (Player player : Bukkit.getOnlinePlayers()) uninject(player);
        originals.clear();
    }

    private Field field(Player player) throws NoSuchFieldException {
        Field cached = permissibleField;
        if (cached != null) return cached;

        Class<?> type = player.getClass();
        while (type != null) {
            for (Field candidate : type.getDeclaredFields()) {
                if (!candidate.getName().equals("perm")) continue;
                if (!Permissible.class.isAssignableFrom(candidate.getType())) continue;
                candidate.setAccessible(true);
                permissibleField = candidate;
                return candidate;
            }
            type = type.getSuperclass();
        }
        throw new NoSuchFieldException("No Permissible field named 'perm' found on " + player.getClass().getName());
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Permissible injection must run on the Paper main thread");
        }
    }
}
