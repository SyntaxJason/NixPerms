package de.astranox.nixperms.web.model.request;

import de.astranox.nixperms.api.group.GroupRole;

public record GroupCreateRequest(String name, GroupRole role) {}

