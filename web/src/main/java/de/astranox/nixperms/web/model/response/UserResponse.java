package de.astranox.nixperms.web.model.response;

import de.astranox.nixperms.api.user.INixUser;

import java.util.Map;
import java.util.UUID;

public record UserResponse(UUID uniqueId, String name, String primaryGroup, String secondaryGroup, Map<String, Boolean> permissions) {
    public static UserResponse from(INixUser user) {
        return new UserResponse(user.uniqueId(), user.name(), user.primary().name(), user.secondaryEffective() != null ? user.secondaryEffective().name() : null, user.ownPermissions());
    }
}
