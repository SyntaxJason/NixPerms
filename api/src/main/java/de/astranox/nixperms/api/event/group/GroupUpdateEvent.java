package de.astranox.nixperms.api.event.group;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.group.IPermissionGroup;

/** Fired once after an atomic group edit has been committed. */
public record GroupUpdateEvent(
        IPermissionGroup previous,
        IPermissionGroup current,
        EventCause cause
) implements GroupEvent { }
