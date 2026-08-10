package de.astranox.nixperms.core.user;

import de.astranox.nixperms.api.group.IMetaEntry;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.core.group.NixGroupManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class NixMetaResolver {

    private final NixGroupManager groups;

    NixMetaResolver(NixGroupManager groups) {
        this.groups = groups;
    }

    NixMetaData resolve(IPermissionGroup primary, @Nullable IPermissionGroup secondary) {
        Map<String, String> options = new HashMap<>();
        MetaValue prefix = new MetaValue(Integer.MIN_VALUE, "");
        MetaValue suffix = new MetaValue(Integer.MIN_VALUE, "");

        for (IPermissionGroup group : groups.getChain(primary)) {
            options.putAll(group.meta().options());
            prefix = strongest(prefix, group.meta().prefixes());
            suffix = strongest(suffix, group.meta().suffixes());
        }

        if (secondary != null) {
            for (IPermissionGroup group : groups.getChain(secondary)) {
                options.putAll(group.meta().options());
                prefix = strongest(prefix, group.meta().prefixes());
                suffix = strongest(suffix, group.meta().suffixes());
            }
        }

        return new NixMetaData(prefix.value(), suffix.value(), Map.copyOf(options));
    }

    private MetaValue strongest(MetaValue current, Iterable<IMetaEntry> entries) {
        MetaValue result = current;
        for (IMetaEntry entry : entries) {
            if (entry.priority() < result.priority()) continue;
            result = new MetaValue(entry.priority(), entry.value());
        }
        return result;
    }

    private record MetaValue(int priority, String value) { }
}
