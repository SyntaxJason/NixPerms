package de.astranox.nixperms.api.user;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IUserManager {
    @Nullable INixUser getUser(UUID uniqueId);
    CompletableFuture<INixUser> loadUser(UUID uniqueId);
    CompletableFuture<INixUser> reloadUser(UUID uniqueId);
    CompletableFuture<INixUser> edit(UUID uniqueId, Consumer<IUserEditor> editor);
    CompletableFuture<Void> saveUser(INixUser user);
    CompletableFuture<@Nullable IUserSnapshot> fetchSnapshot(UUID uniqueId);
    CompletableFuture<@Nullable INixUser> resolveUser(String nameOrUuid);
    void unloadUser(UUID uniqueId);
    Collection<INixUser> loaded();
}
