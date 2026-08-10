package de.astranox.nixperms.api.event.group;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.group.IPermissionGroup;

public record GroupCreateEvent(IPermissionGroup group, EventCause cause) implements GroupEvent {}
