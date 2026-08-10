package de.astranox.nixperms.web.model.response;

import de.astranox.nixperms.api.group.IPermissionGroup;

import java.util.List;
import java.util.Map;

public record GroupResponse(String name, String role, int weight, String parentName, Map<String, Boolean> permissions, List<MetaEntryResponse> prefixes, List<MetaEntryResponse> suffixes, Map<String, String> options) {
    public record MetaEntryResponse(int priority, String value) {}
    public static GroupResponse from(IPermissionGroup group) {
        return new GroupResponse(group.name(), group.role().name(), group.weight(), group.parent().map(IPermissionGroup::name).orElse(null), group.permissions().asMap(), group.meta().prefixes().stream().map(e -> new MetaEntryResponse(e.priority(), e.value())).toList(), group.meta().suffixes().stream().map(e -> new MetaEntryResponse(e.priority(), e.value())).toList(), group.meta().options());
    }
}

