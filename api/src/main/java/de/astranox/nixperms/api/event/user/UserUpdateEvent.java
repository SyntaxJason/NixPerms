package de.astranox.nixperms.api.event.user;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.user.IUserSnapshot;

/** Fired once after an atomic user edit has been committed. */
public record UserUpdateEvent(
        IUserSnapshot previous,
        IUserSnapshot current,
        EventCause cause
) implements UserEvent { }
