package de.astranox.nixperms.core;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.attachment.IAttachmentService;
import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.event.network.NetworkSyncEvent;
import de.astranox.nixperms.api.group.IGroupManager;
import de.astranox.nixperms.api.message.IMessageService;
import de.astranox.nixperms.api.sync.ISyncNotifier;
import de.astranox.nixperms.api.user.IUserManager;
import de.astranox.nixperms.core.attachment.NixAttachmentService;
import de.astranox.nixperms.core.command.AnnotationCommandProcessor;
import de.astranox.nixperms.core.config.NixPermsSettings;
import de.astranox.nixperms.core.database.HikariPoolFactory;
import de.astranox.nixperms.core.database.SQLDatabase;
import de.astranox.nixperms.core.event.NixEventBus;
import de.astranox.nixperms.core.group.NixGroupManager;
import de.astranox.nixperms.core.message.MessageRegistry;
import de.astranox.nixperms.core.message.MessageService;
import de.astranox.nixperms.core.message.locale.CommonMessages_en_us;
import de.astranox.nixperms.core.message.locale.GroupMessages_en_us;
import de.astranox.nixperms.core.message.locale.UserMessages_en_us;
import de.astranox.nixperms.core.permission.NixPermissionResolver;
import de.astranox.nixperms.core.storage.GroupStorageAdapter;
import de.astranox.nixperms.core.storage.UserStorageAdapter;
import de.astranox.nixperms.core.sync.NixSyncNotifier;
import de.astranox.nixperms.core.sync.SQLSyncStorage;
import de.astranox.nixperms.core.sync.SyncMessenger;
import de.astranox.nixperms.core.user.NixUserManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class NixPermsCore implements INixPermsAPI, AutoCloseable {

    public static final String VERSION = "0.1.0";

    private final NixPermsSettings settings;
    private final SQLDatabase database;
    private final ExecutorService storageExecutor;
    private final ExecutorService computeExecutor;
    private final NixEventBus eventBus;
    private final NixGroupManager groupManager;
    private final NixUserManager userManager;
    private final NixAttachmentService attachmentService;
    private final MessageService messageService;
    private final NixSyncNotifier syncNotifier;
    private final SyncMessenger syncMessenger;
    private final AnnotationCommandProcessor commandProcessor;
    private final AtomicBoolean closed = new AtomicBoolean();

    private NixPermsCore(NixPermsSettings settings) {
        if (settings == null) throw new IllegalArgumentException("NixPerms settings cannot be null");
        this.settings = settings;
        this.database = new SQLDatabase(
                HikariPoolFactory.create(settings.database()),
                settings.database().dialect(), settings.network()
        );
        this.storageExecutor = Executors.newFixedThreadPool(
                settings.databaseThreads(), new StorageThreadFactory()
        );
        int computeThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.computeExecutor = Executors.newFixedThreadPool(
                computeThreads, new ComputeThreadFactory()
        );
        try {
            this.eventBus = new NixEventBus();
            this.messageService = new MessageService(buildMessageRegistry());
            this.syncNotifier = new NixSyncNotifier(messageService);

            this.groupManager = new NixGroupManager(
                    new GroupStorageAdapter(database), eventBus, storageExecutor, settings.defaultGroup()
            );
            NixPermissionResolver resolver = new NixPermissionResolver(groupManager::getChain);
            this.userManager = new NixUserManager(
                    new UserStorageAdapter(database), groupManager, resolver, eventBus,
                    settings.resolutionPolicy(), storageExecutor, computeExecutor, settings.network().serverId()
            );
            this.groupManager.onGroupChanged(userManager::invalidateGroup);
            this.attachmentService = new NixAttachmentService(userManager);
            this.syncMessenger = new SyncMessenger(
                    new SQLSyncStorage(database), eventBus, settings.network(), this::applyNetworkUpdate
            );
            this.commandProcessor = new AnnotationCommandProcessor(this);
        } catch (RuntimeException | Error failure) {
            storageExecutor.shutdownNow();
            computeExecutor.shutdownNow();
            database.close();
            throw failure;
        }
    }

    public static CompletableFuture<NixPermsCore> create(NixPermsSettings settings) {
        NixPermsCore core = new NixPermsCore(settings);
        try {
            core.syncMessenger.captureBaseline();
        } catch (RuntimeException failure) {
            core.close();
            return CompletableFuture.failedFuture(failure);
        }
        CompletableFuture<NixPermsCore> startup = core.groupManager.loadAll().thenApply(ignored -> {
            core.syncMessenger.start();
            return core;
        });
        startup.whenComplete((result, error) -> {
            if (error != null) core.close();
        });
        return startup;
    }

    public CompletableFuture<Void> reloadData() {
        return groupManager.loadAll().thenCompose(ignored -> userManager.reloadAllLoaded());
    }

    public AnnotationCommandProcessor commands() {
        return commandProcessor;
    }

    public SyncMessenger sync() {
        return syncMessenger;
    }

    public NixPermsSettings settings() {
        return settings;
    }

    @Override public String version() { return VERSION; }
    @Override public String serverId() { return settings.network().serverId(); }
    @Override public IUserManager users() { return userManager; }
    @Override public IGroupManager groups() { return groupManager; }
    @Override public IAttachmentService attachments() { return attachmentService; }
    @Override public IEventBus events() { return eventBus; }
    @Override public IMessageService messages() { return messageService; }
    @Override public ISyncNotifier syncNotifier() { return syncNotifier; }

    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        syncMessenger.stop();
        storageExecutor.shutdown();
        computeExecutor.shutdown();
        try {
            if (!storageExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                storageExecutor.shutdownNow();
            }
            if (!computeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                computeExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            storageExecutor.shutdownNow();
            computeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            database.close();
        }
    }

    private CompletableFuture<Void> applyNetworkUpdate(NetworkSyncEvent event) {
        CompletableFuture<?> refresh = switch (event.syncType()) {
            case USER_UPDATE -> userManager.getUser(event.affectedUserId()) == null
                    ? CompletableFuture.completedFuture(null)
                    : userManager.reloadUser(event.affectedUserId());
            case GROUP_UPDATE -> groupManager.reloadGroup(event.affectedGroupName());
            case GLOBAL_INVALIDATION -> reloadData();
        };
        return refresh.thenApply(ignored -> null);
    }

    private MessageRegistry buildMessageRegistry() {
        MessageRegistry registry = new MessageRegistry();
        registry.setDefaultLocale("en_us");
        registry.register(GroupMessages_en_us.class);
        registry.register(UserMessages_en_us.class);
        registry.register(CommonMessages_en_us.class);
        return registry;
    }

    private static final class StorageThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "nixperms-storage-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class ComputeThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "nixperms-compute-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
