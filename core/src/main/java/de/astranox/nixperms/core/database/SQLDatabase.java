package de.astranox.nixperms.core.database;

import com.zaxxer.hikari.HikariDataSource;
import de.astranox.nixperms.core.config.NetworkSettings;
import de.astranox.nixperms.core.database.repo.SQLGroupRepository;
import de.astranox.nixperms.core.database.repo.SQLSyncRepository;
import de.astranox.nixperms.core.database.repo.SQLUserRepository;
import de.astranox.nixperms.core.model.GroupModel;
import de.astranox.nixperms.core.model.UserModel;
import de.astranox.nixperms.core.sync.SyncMessage;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class SQLDatabase implements AutoCloseable {

    private static final int SYNC_BATCH_SIZE = 1_000;

    private final HikariDataSource pool;
    private final SqlDialect dialect;
    private final boolean networkEnabled;
    private final String serverId;
    private final SQLGroupRepository groups;
    private final SQLUserRepository users;
    private final SQLSyncRepository sync = new SQLSyncRepository();

    public SQLDatabase(HikariDataSource pool, SqlDialect dialect, NetworkSettings network) {
        if (pool == null) throw new IllegalArgumentException("Connection pool cannot be null");
        if (dialect == null) throw new IllegalArgumentException("SQL dialect cannot be null");
        if (network == null) throw new IllegalArgumentException("Network settings cannot be null");
        this.pool = pool;
        this.dialect = dialect;
        this.networkEnabled = network.enabled();
        this.serverId = network.serverId();
        this.groups = new SQLGroupRepository(dialect);
        this.users = new SQLUserRepository(dialect);
        try {
            initialize();
        } catch (RuntimeException | Error failure) {
            pool.close();
            throw failure;
        }
    }

    public @Nullable GroupModel getGroup(String name) {
        return transaction("load group " + name, connection -> groups.get(connection, name));
    }

    public Collection<GroupModel> getAllGroups() {
        return transaction("load groups", groups::getAll);
    }

    public void saveGroup(GroupModel model, boolean broadcast) {
        transaction("save group " + model.name(), connection -> {
            groups.save(connection, model);
            publishChange(connection, broadcast, "GROUP_UPDATE", model.name());
            return null;
        });
    }

    public void deleteGroup(String name, boolean broadcast) {
        transaction("delete group " + name, connection -> {
            groups.delete(connection, name);
            publishChange(connection, broadcast, "GROUP_UPDATE", name);
            return null;
        });
    }

    public @Nullable UserModel getUser(UUID uniqueId) {
        return transaction("load user " + uniqueId, connection -> users.get(connection, uniqueId));
    }

    public @Nullable UserModel getUserByName(String name) {
        if (name == null || name.isBlank()) return null;
        return transaction("load user " + name, connection -> users.getByName(connection, name));
    }

    public void saveUser(UserModel model, boolean broadcast) {
        transaction("save user " + model.uniqueId(), connection -> {
            users.save(connection, model);
            publishChange(connection, broadcast, "USER_UPDATE", model.uniqueId().toString());
            return null;
        });
    }

    public void publishSync(SyncMessage message) {
        if (!networkEnabled) return;
        transaction("publish sync update", connection -> {
            sync.publish(connection, message);
            return null;
        });
    }

    public List<SyncMessage> pollSync(long lastId) {
        if (!networkEnabled) return List.of();
        return read("poll sync updates", connection ->
                sync.pollSince(connection, lastId, SYNC_BATCH_SIZE));
    }

    public long latestSyncId() {
        if (!networkEnabled) return 0L;
        return read("load latest sync id", sync::latestId);
    }

    public int cleanupSync(long maxAgeMillis) {
        if (!networkEnabled) return 0;
        return transaction("clean sync updates", connection -> sync.cleanup(connection, maxAgeMillis));
    }

    @Override
    public void close() {
        pool.close();
    }

    public void disconnect() {
        close();
    }

    private void initialize() {
        read("initialize schema", connection -> {
            if (dialect == SqlDialect.SQLITE) configureSqlite(connection);
            SchemaInitializer.initialize(connection, dialect);
            return null;
        });
    }

    private void configureSqlite(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void publishChange(Connection connection, boolean broadcast, String type, String payload)
            throws SQLException {
        if (!broadcast || !networkEnabled) return;
        sync.publish(connection, new SyncMessage(0L, serverId, type, payload));
    }

    private <T> T read(String action, SqlWork<T> work) {
        try (Connection connection = pool.getConnection()) {
            return work.execute(connection);
        } catch (SQLException exception) {
            throw new StorageException("Could not " + action, exception);
        }
    }

    private <T> T transaction(String action, SqlWork<T> work) {
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) {
                    throw new StorageException("Could not " + action, sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not " + action, exception);
        }
    }

    private void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
