package de.astranox.nixperms.core.profile;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface IProfileResolver {

    CompletableFuture<@Nullable ResolvedProfile> resolve(String name);
}
