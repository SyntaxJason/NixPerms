package de.astranox.nixperms.api.event.group;

import de.astranox.nixperms.api.event.EventCause;
import de.astranox.nixperms.api.group.IPermissionGroup;

public record GroupMetaChangeEvent(IPermissionGroup group, MetaChangeType changeType, EventCause cause) implements GroupEvent {
    public enum MetaChangeType { PREFIX_ADDED, PREFIX_REMOVED, SUFFIX_ADDED, SUFFIX_REMOVED, OPTION_SET, OPTION_UNSET, WEIGHT_CHANGED, PARENT_CHANGED }
}
