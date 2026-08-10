package de.astranox.nixperms.api.event.user;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.api.user.INixUser;
import org.jetbrains.annotations.Nullable;

public record UserGroupChangeEvent(INixUser user, IPermissionGroup newPrimary, @Nullable IPermissionGroup newSecondary, EventCause cause) implements UserEvent {}
