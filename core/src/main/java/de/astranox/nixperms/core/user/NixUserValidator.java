package de.astranox.nixperms.core.user;

import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.core.group.NixGroupManager;
import de.astranox.nixperms.core.model.UserModel;

final class NixUserValidator {

    private final NixGroupManager groups;

    NixUserValidator(NixGroupManager groups) {
        this.groups = groups;
    }

    void validate(UserModel model) {
        IPermissionGroup primary = groups.group(model.primaryGroupName());
        if (primary == null) throw new IllegalArgumentException("Unknown primary group: " + model.primaryGroupName());
        if (primary.role() != GroupRole.PRIMARY) {
            throw new IllegalArgumentException("Primary group must have role PRIMARY");
        }

        String secondaryName = model.secondaryGroupName();
        if (secondaryName == null) return;
        IPermissionGroup secondary = groups.group(secondaryName);
        if (secondary == null) throw new IllegalArgumentException("Unknown secondary group: " + secondaryName);
        if (secondary.role() != GroupRole.SECONDARY) {
            throw new IllegalArgumentException("Secondary group must have role SECONDARY");
        }
    }
}
