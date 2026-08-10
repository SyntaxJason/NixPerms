package de.astranox.nixperms.api.event.user;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.user.INixUser;
import org.jetbrains.annotations.Nullable;

public record UserPermissionChangeEvent(INixUser user, String node, @Nullable Boolean newValue, EventCause cause) implements UserEvent {}
