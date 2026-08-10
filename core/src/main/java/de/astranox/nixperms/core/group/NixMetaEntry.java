package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.group.IMetaEntry;

public record NixMetaEntry(int priority, String value) implements IMetaEntry {}
