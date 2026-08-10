package de.astranox.nixperms.core.sync;

import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.event.network.NetworkSyncEvent;
import de.astranox.nixperms.api.sync.ISyncProvider;
import de.astranox.nixperms.core.config.NetworkSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class SyncMessenger implements ISyncProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncMessenger.class);

    private final SQLSyncStorage storage;
    private final IEventBus eventBus;
    private final NetworkSettings settings;
    private final Function<NetworkSyncEvent, CompletableFuture<Void>> updateHandler;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long lastProcessedId;
    private volatile long nextCleanupAt;
    private volatile long failureSince;
    private volatile boolean baselineCaptured;
    private ScheduledFuture<?> pollTask;

    public SyncMessenger(
            SQLSyncStorage storage,
            IEventBus eventBus,
            NetworkSettings settings,
            Function<NetworkSyncEvent, CompletableFuture<Void>> updateHandler
    ) {
        if (storage == null) throw new IllegalArgumentException("Sync storage cannot be null");
        if (eventBus == null) throw new IllegalArgumentException("Event bus cannot be null");
        if (settings == null) throw new IllegalArgumentException("Network settings cannot be null");
        if (updateHandler == null) throw new IllegalArgumentException("Network update handler cannot be null");
        this.storage = storage;
        this.eventBus = eventBus;
        this.settings = settings;
        this.updateHandler = updateHandler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "nixperms-sync-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start() {
        if (!settings.enabled()) return;
        if (!baselineCaptured) captureBaseline();
        if (!running.compareAndSet(false, true)) return;
        nextCleanupAt = System.currentTimeMillis() + cleanupIntervalMillis();
        long interval = settings.pollInterval().toMillis();
        pollTask = scheduler.scheduleWithFixedDelay(this::safePoll, interval, interval, TimeUnit.MILLISECONDS);
    }

    /** Captures the cursor before the initial cache load, closing the startup race window. */
    public synchronized void captureBaseline() {
        if (!settings.enabled() || baselineCaptured) return;
        if (running.get()) throw new IllegalStateException("Sync polling has already started");
        lastProcessedId = storage.latestId();
        baselineCaptured = true;
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            awaitScheduler();
            return;
        }
        if (pollTask != null) pollTask.cancel(false);
        scheduler.shutdownNow();
        awaitScheduler();
        try {
            storage.cleanup(settings.retention().toMillis());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not clean the sync table during shutdown", exception);
        }
    }

    @Override
    public void publishUserUpdate(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("User UUID cannot be null");
        publish("USER_UPDATE", userId.toString());
    }

    @Override
    public void publishGroupUpdate(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("Group name cannot be blank");
        }
        publish("GROUP_UPDATE", groupName.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public void publishGlobalInvalidation() {
        publish("GLOBAL_INVALIDATION", null);
    }

    public boolean enabled() {
        return settings.enabled();
    }

    private void publish(String type, String payload) {
        if (!settings.enabled()) return;
        storage.publish(new SyncMessage(0L, settings.serverId(), type, payload));
    }

    private void safePoll() {
        if (!running.get()) return;
        try {
            recoverIfPollingWasOfflineTooLong();
            poll();
            cleanupWhenDue();
            failureSince = 0L;
        } catch (RuntimeException exception) {
            if (failureSince == 0L) failureSince = System.currentTimeMillis();
            LOGGER.warn("NixPerms sync polling failed; the next poll will retry", exception);
        }
    }

    private void poll() {
        List<SyncMessage> messages = storage.pollSince(lastProcessedId);
        if (messages.isEmpty()) return;

        long batchCursor = lastProcessedId;
        Map<String, NetworkSyncEvent> updates = new LinkedHashMap<>();
        NetworkSyncEvent global = null;

        for (SyncMessage message : messages) {
            batchCursor = message.id();
            if (settings.serverId().equals(message.serverId())) continue;
            try {
                NetworkSyncEvent event = event(message);
                if (event == null) continue;
                if (event.syncType() == NetworkSyncEvent.SyncType.GLOBAL_INVALIDATION) {
                    global = event;
                    updates.clear();
                    continue;
                }
                if (global != null) continue;
                updates.put(coalesceKey(event), event);
            } catch (RuntimeException exception) {
                if (!malformed(message, exception)) throw exception;
            }
        }

        if (global != null) {
            apply(global);
        } else {
            updates.values().forEach(this::apply);
        }
        lastProcessedId = batchCursor;
    }

    private String coalesceKey(NetworkSyncEvent event) {
        return switch (event.syncType()) {
            case USER_UPDATE -> "user:" + event.affectedUserId();
            case GROUP_UPDATE -> "group:" + event.affectedGroupName();
            case GLOBAL_INVALIDATION -> "global";
        };
    }

    private void apply(NetworkSyncEvent event) {
        updateHandler.apply(event).join();
        eventBus.post(event);
    }

    private NetworkSyncEvent event(SyncMessage message) {
        return switch (message.type()) {
            case "USER_UPDATE" -> new NetworkSyncEvent(
                    NetworkSyncEvent.SyncType.USER_UPDATE,
                    UUID.fromString(requiredPayload(message)), null, message.serverId()
            );
            case "GROUP_UPDATE" -> new NetworkSyncEvent(
                    NetworkSyncEvent.SyncType.GROUP_UPDATE,
                    null, requiredPayload(message), message.serverId()
            );
            case "GLOBAL_INVALIDATION" -> new NetworkSyncEvent(
                    NetworkSyncEvent.SyncType.GLOBAL_INVALIDATION,
                    null, null, message.serverId()
            );
            default -> null;
        };
    }

    private boolean malformed(SyncMessage message, RuntimeException exception) {
        if (exception instanceof java.util.concurrent.CompletionException) return false;
        LOGGER.warn("Ignoring malformed sync message {} from {}", message.id(), message.serverId(), exception);
        return true;
    }

    private String requiredPayload(SyncMessage message) {
        if (message.payload() == null || message.payload().isBlank()) {
            throw new IllegalArgumentException("Sync payload is missing for " + message.type());
        }
        return message.payload();
    }

    private void cleanupWhenDue() {
        long now = System.currentTimeMillis();
        if (now < nextCleanupAt) return;
        storage.cleanup(settings.retention().toMillis());
        nextCleanupAt = now + cleanupIntervalMillis();
    }

    private void recoverIfPollingWasOfflineTooLong() {
        long failedAt = failureSince;
        if (failedAt == 0L) return;
        if (System.currentTimeMillis() - failedAt < settings.retention().toMillis()) return;

        NetworkSyncEvent recovery = new NetworkSyncEvent(
                NetworkSyncEvent.SyncType.GLOBAL_INVALIDATION,
                null, null, "database-recovery"
        );
        updateHandler.apply(recovery).join();
        lastProcessedId = storage.latestId();
        eventBus.post(recovery);
        failureSince = 0L;
    }

    private long cleanupIntervalMillis() {
        return Math.max(60_000L, settings.retention().toMillis() / 4L);
    }

    private void awaitScheduler() {
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("NixPerms sync poller did not stop within five seconds");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
