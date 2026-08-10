package de.astranox.nixperms.core.storage;

import de.astranox.nixperms.core.database.SQLDatabase;
import de.astranox.nixperms.core.model.GroupModel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class GroupStorageAdapter implements IGroupStorage {

    private final SQLDatabase database;

    public GroupStorageAdapter(SQLDatabase database) {
        this.database = database;
    }

    @Override public Collection<GroupModel> loadAll() { return database.getAllGroups(); }
    @Override public @Nullable GroupModel load(String name) { return database.getGroup(name); }
    @Override public void save(GroupModel model, boolean broadcast) { database.saveGroup(model, broadcast); }
    @Override public void delete(String name, boolean broadcast) { database.deleteGroup(name, broadcast); }
}
