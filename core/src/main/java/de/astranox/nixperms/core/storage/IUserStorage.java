package de.astranox.nixperms.core.storage;

import de.astranox.nixperms.core.model.UserModel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface IUserStorage {
    @Nullable UserModel load(UUID uniqueId);
    @Nullable UserModel loadByName(String name);
    void save(UserModel model, boolean broadcast);
}
