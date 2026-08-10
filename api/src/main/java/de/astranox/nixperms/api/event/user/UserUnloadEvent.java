package de.astranox.nixperms.api.event.user;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.user.IUserSnapshot;

public record UserUnloadEvent(IUserSnapshot snapshot, EventCause cause) implements UserEvent {}
