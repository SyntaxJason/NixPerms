package de.astranox.nixperms.api.event.user;

import de.astranox.nixperms.api.event.INixEvent;

public sealed interface UserEvent extends INixEvent
        permits UserLoadEvent, UserUnloadEvent, UserPermissionChangeEvent, UserGroupChangeEvent,
        UserUpdateEvent, UserEffectivePermissionsChangeEvent {}
