package de.astranox.nixperms.api.user;

import java.util.Map;

public interface IMetaData {
    String prefix();
    String suffix();
    Map<String, String> options();
    String option(String key);
}
