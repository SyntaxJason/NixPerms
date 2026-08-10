package de.astranox.nixperms.core.storage;

import de.astranox.nixperms.core.database.SQLDatabase;
import de.astranox.nixperms.core.model.UserModel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class UserStorageAdapter implements IUserStorage {

    private final SQLDatabase database;

    public UserStorageAdapter(SQLDatabase database) {
        this.database = database;
    }

    @Override public @Nullable UserModel load(UUID uniqueId) { return database.getUser(uniqueId); }
    @Override public @Nullable UserModel loadByName(String name) { return database.getUserByName(name); }
    @Override public void save(UserModel model, boolean broadcast) { database.saveUser(model, broadcast); }
}
