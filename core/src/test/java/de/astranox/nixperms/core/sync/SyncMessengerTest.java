package de.astranox.nixperms.core.sync;

import de.astranox.nixperms.core.config.DatabaseSettings;
import de.astranox.nixperms.core.config.NetworkSettings;
import de.astranox.nixperms.core.database.HikariPoolFactory;
import de.astranox.nixperms.core.database.SQLDatabase;
import de.astranox.nixperms.core.database.SqlDialect;
import de.astranox.nixperms.core.event.NixEventBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncMessengerTest {

    @TempDir Path temporaryDirectory;

    @Test
    void retriesMessageWhenCacheRefreshFails() throws Exception {
        NetworkSettings settings = new NetworkSettings(
                true, "alpha", Duration.ofMillis(20), Duration.ofMinutes(2)
        );
        DatabaseSettings storage = DatabaseSettings.sqlite(temporaryDirectory.resolve("sync.db"));
        try (SQLDatabase database = new SQLDatabase(
                HikariPoolFactory.create(storage), SqlDialect.SQLITE, settings
        )) {
            AtomicInteger attempts = new AtomicInteger();
            CountDownLatch applied = new CountDownLatch(1);
            SyncMessenger messenger = new SyncMessenger(
                    new SQLSyncStorage(database), new NixEventBus(), settings, event -> {
                        if (attempts.incrementAndGet() == 1) {
                            return CompletableFuture.failedFuture(new IllegalStateException("temporary"));
                        }
                        applied.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
            );
            messenger.captureBaseline();
            database.publishSync(new SyncMessage(0L, "beta", "GROUP_UPDATE", "default"));
            messenger.start();
            try {
                assertTrue(applied.await(2, TimeUnit.SECONDS));
                assertTrue(attempts.get() >= 2);
            } finally {
                messenger.stop();
            }
        }
    }

    @Test
    void coalescesRepeatedEntityUpdatesFromTheSamePollBatch() throws Exception {
        NetworkSettings settings = new NetworkSettings(
                true, "alpha", Duration.ofMillis(20), Duration.ofMinutes(2)
        );
        DatabaseSettings storage = DatabaseSettings.sqlite(temporaryDirectory.resolve("coalesce.db"));
        try (SQLDatabase database = new SQLDatabase(
                HikariPoolFactory.create(storage), SqlDialect.SQLITE, settings
        )) {
            AtomicInteger applied = new AtomicInteger();
            CountDownLatch firstUpdate = new CountDownLatch(1);
            SyncMessenger messenger = new SyncMessenger(
                    new SQLSyncStorage(database), new NixEventBus(), settings, event -> {
                        applied.incrementAndGet();
                        firstUpdate.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
            );
            messenger.captureBaseline();
            for (int index = 0; index < 100; index++) {
                database.publishSync(new SyncMessage(0L, "beta", "GROUP_UPDATE", "default"));
            }
            messenger.start();
            try {
                assertTrue(firstUpdate.await(2, TimeUnit.SECONDS));
                Thread.sleep(100L);
                org.junit.jupiter.api.Assertions.assertEquals(1, applied.get());
            } finally {
                messenger.stop();
            }
        }
    }
}
