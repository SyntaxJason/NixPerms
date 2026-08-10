package de.astranox.nixperms.paper;

import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.event.user.UserEffectivePermissionsChangeEvent;
import de.astranox.nixperms.core.NixPermsCore;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Coalesces permission changes into at most one Paper command-tree update per player and tick. */
final class BukkitCommandTreeUpdater implements AutoCloseable {

    private final NixPermsBukkit plugin;
    private final IEventBus eventBus;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<UserEffectivePermissionsChangeEvent> permissionListener;

    BukkitCommandTreeUpdater(NixPermsBukkit plugin, NixPermsCore core) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (core == null) throw new IllegalArgumentException("NixPerms core cannot be null");
        this.plugin = plugin;
        this.eventBus = core.events();
        this.permissionListener = event -> request(event.user().uniqueId());
        eventBus.subscribe(UserEffectivePermissionsChangeEvent.class, permissionListener);
    }

    void request(Player player) {
        if (player == null) return;
        request(player.getUniqueId());
    }

    void request(UUID uniqueId) {
        if (uniqueId == null || closed.get()) return;
        pending.add(uniqueId);
        schedule();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        eventBus.unsubscribe(UserEffectivePermissionsChangeEvent.class, permissionListener);
        pending.clear();
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, this::flush);
        } catch (RuntimeException error) {
            scheduled.set(false);
            if (!closed.get() && plugin.isEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Could not schedule a command-tree refresh", error);
            }
        }
    }

    private void flush() {
        if (closed.get()) {
            pending.clear();
            scheduled.set(false);
            return;
        }

        List<UUID> updates = List.copyOf(pending);
        pending.removeAll(updates);
        for (UUID uniqueId : updates) {
            Player player = plugin.getServer().getPlayer(uniqueId);
            if (player == null || !player.isOnline()) continue;
            try {
                player.updateCommands();
            } catch (RuntimeException error) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Could not refresh the command tree for " + player.getName(),
                        error
                );
            }
        }

        scheduled.set(false);
        if (!pending.isEmpty()) schedule();
    }
}
