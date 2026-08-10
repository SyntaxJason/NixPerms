package de.astranox.nixperms.core.sync;

import de.astranox.nixperms.core.database.SQLDatabase;
import java.util.List;

public final class SQLSyncStorage {

    private final SQLDatabase db;

    public SQLSyncStorage(SQLDatabase db) { this.db = db; }

    public void publish(SyncMessage message) { db.publishSync(message); }
    public List<SyncMessage> pollSince(long lastId) { return db.pollSync(lastId); }
    public int cleanup(long maxAgeMs) { return db.cleanupSync(maxAgeMs); }
    public long latestId() { return db.latestSyncId(); }
}
