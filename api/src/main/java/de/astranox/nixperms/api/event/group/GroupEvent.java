package de.astranox.nixperms.api.event.group;

import de.astranox.nixperms.api.event.INixEvent;

public sealed interface GroupEvent extends INixEvent
        permits GroupCreateEvent, GroupDeleteEvent, GroupPermissionChangeEvent, GroupMetaChangeEvent,
        GroupUpdateEvent {}
