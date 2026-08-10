package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.group.IGroupMeta;
import de.astranox.nixperms.api.group.IMetaEntry;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NixGroupMeta implements IGroupMeta {

    private final Map<String, String> options;
    private final List<IMetaEntry> prefixes;
    private final List<IMetaEntry> suffixes;

    public NixGroupMeta(
            Map<String, String> options,
            List<? extends IMetaEntry> prefixes,
            List<? extends IMetaEntry> suffixes
    ) {
        this.options = Map.copyOf(options);
        this.prefixes = List.copyOf(prefixes);
        this.suffixes = List.copyOf(suffixes);
    }

    @Override public Map<String, String> options() { return options; }
    @Override public String option(String key) {
        if (key == null) return "";
        return options.getOrDefault(key.trim().toLowerCase(Locale.ROOT), "");
    }
    @Override public List<IMetaEntry> prefixes() { return prefixes; }
    @Override public List<IMetaEntry> suffixes() { return suffixes; }
    @Override public String primaryPrefix() { return prefixes.isEmpty() ? "" : prefixes.get(0).value(); }
    @Override public String primarySuffix() { return suffixes.isEmpty() ? "" : suffixes.get(0).value(); }
}
