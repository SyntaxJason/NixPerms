package de.astranox.nixperms.core.model;

import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.permission.PermissionRule;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record GroupModel(
        String name,
        GroupRole role,
        int weight,
        @Nullable String parentName,
        @Nullable String defaultSecondaryName,
        List<PermissionRule> rules,
        List<MetaEntryModel> prefixes,
        List<MetaEntryModel> suffixes,
        Map<String, String> options
) {

    public GroupModel {
        name = normalizeName(name);
        if (role == null) throw new IllegalArgumentException("Group role cannot be null");
        parentName = nullableName(parentName);
        defaultSecondaryName = nullableName(defaultSecondaryName);
        rules = rules == null ? List.of() : List.copyOf(rules);
        prefixes = prefixes == null ? List.of() : List.copyOf(prefixes);
        suffixes = suffixes == null ? List.of() : List.copyOf(suffixes);
        options = normalizeOptions(options);
    }

    public static GroupModel empty(String name, GroupRole role) {
        return new GroupModel(name, role, 0, null, null, List.of(), List.of(), List.of(), Map.of());
    }

    public static String normalizeName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Group name cannot be blank");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Invalid group name: " + value);
        }
        return normalized;
    }

    private static @Nullable String nullableName(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        return normalizeName(value);
    }

    private static Map<String, String> normalizeOptions(Map<String, String> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, String> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Option key cannot be blank");
            String safeKey = key.trim().toLowerCase(Locale.ROOT);
            if (safeKey.length() > 64) throw new IllegalArgumentException("Option key is longer than 64 characters");
            if (value == null) throw new IllegalArgumentException("Option value cannot be null");
            if (value.length() > 512) throw new IllegalArgumentException("Option value is longer than 512 characters");
            normalized.put(safeKey, value);
        });
        return Map.copyOf(normalized);
    }
}
