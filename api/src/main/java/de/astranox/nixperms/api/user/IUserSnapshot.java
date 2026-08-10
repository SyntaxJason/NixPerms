package de.astranox.nixperms.api.user;

import de.astranox.nixperms.api.permission.IPermissionData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface IUserSnapshot {
    UUID uniqueId();
    @Nullable String name();
    String primaryGroupName();
    @Nullable String secondaryExplicitName();
    @Nullable String secondaryEffectiveName();
    IPermissionData permissions();
    IMetaData meta();
}
