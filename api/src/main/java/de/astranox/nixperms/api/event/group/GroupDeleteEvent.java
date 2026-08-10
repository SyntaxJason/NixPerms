package de.astranox.nixperms.api.event.group;

import de.astranox.nixperms.api.event.EventCause;

public record GroupDeleteEvent(String groupName, EventCause cause) implements GroupEvent {}
