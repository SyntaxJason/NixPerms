package de.astranox.nixperms.api.group;

import java.util.List;
import java.util.Map;

public interface IGroupMeta {
    Map<String, String> options();
    String option(String key);
    List<IMetaEntry> prefixes();
    List<IMetaEntry> suffixes();
    String primaryPrefix();
    String primarySuffix();
}
