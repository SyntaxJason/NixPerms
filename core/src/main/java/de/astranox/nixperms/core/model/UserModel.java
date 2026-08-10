package de.astranox.nixperms.core.model;

import de.astranox.nixperms.api.permission.PermissionRule;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record UserModel(
        UUID uniqueId,
        @Nullable String name,
        String primaryGroupName,
        @Nullable String secondaryGroupName,
        List<PermissionRule> rules
) {

    public UserModel {
        if (uniqueId == null) throw new IllegalArgumentException("User UUID cannot be null");
        name = normalizeNullable(name);
        primaryGroupName = GroupModel.normalizeName(primaryGroupName);
        secondaryGroupName = normalizeGroupNullable(secondaryGroupName);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static UserModel create(UUID uniqueId, String defaultGroup) {
        return new UserModel(uniqueId, null, defaultGroup, null, List.of());
    }

    private static @Nullable String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 64) throw new IllegalArgumentException("User name is longer than 64 characters");
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException("User name contains control characters");
            }
        }
        return normalized;
    }

    private static @Nullable String normalizeGroupNullable(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        return GroupModel.normalizeName(value);
    }
}
