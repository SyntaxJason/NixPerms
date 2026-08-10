package de.astranox.nixperms.api.event.user;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.user.INixUser;

public record UserLoadEvent(INixUser user, EventCause cause) implements UserEvent {}
