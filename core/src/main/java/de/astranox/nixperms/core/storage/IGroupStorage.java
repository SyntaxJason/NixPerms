package de.astranox.nixperms.core.storage;

import de.astranox.nixperms.core.model.GroupModel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface IGroupStorage {
    Collection<GroupModel> loadAll();
    @Nullable GroupModel load(String name);
    void save(GroupModel model, boolean broadcast);
    void delete(String name, boolean broadcast);
}
