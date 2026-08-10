package de.astranox.nixperms.api.event.user;

import de.astranox.nixperms.api.permission.IPermissionData;
import de.astranox.nixperms.api.user.INixUser;

/**
 * Fired after the effective permission snapshot of a loaded user changes.
 * This also covers changes caused by groups, contexts and runtime attachments.
 */
public record UserEffectivePermissionsChangeEvent(
        INixUser user,
        IPermissionData previous,
        IPermissionData current
) implements UserEvent { }
