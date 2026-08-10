package de.astranox.nixperms.api.event.group;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.group.IPermissionGroup;
import org.jetbrains.annotations.Nullable;

public record GroupPermissionChangeEvent(IPermissionGroup group, String node, @Nullable Boolean newValue, @Nullable Boolean previousValue, EventCause cause) implements GroupEvent {}
