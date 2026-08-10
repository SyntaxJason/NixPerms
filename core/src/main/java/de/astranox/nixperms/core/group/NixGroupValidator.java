package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.core.model.GroupModel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class NixGroupValidator {

    private final Map<String, GroupModel> groups;

    NixGroupValidator(Map<String, GroupModel> groups) {
        this.groups = groups;
    }

    void validate(GroupModel model) {
        validateParent(model);
        validateDefaultSecondary(model);
    }

    void validateDelete(String name, String defaultGroupName) {
        if (name.equals(defaultGroupName)) {
            throw new IllegalArgumentException("The default group cannot be deleted");
        }
        for (GroupModel group : groups.values()) {
            if (name.equals(group.parentName())) {
                throw new IllegalStateException("Group is parent of " + group.name());
            }
            if (name.equals(group.defaultSecondaryName())) {
                throw new IllegalStateException("Group is default secondary of " + group.name());
            }
        }
    }

    private void validateParent(GroupModel model) {
        String parentName = model.parentName();
        if (parentName == null) return;
        if (parentName.equals(model.name())) throw new IllegalArgumentException("A group cannot inherit itself");

        GroupModel parent = groups.get(parentName);
        if (parent == null) throw new IllegalArgumentException("Unknown parent group: " + parentName);
        if (parent.role() != model.role()) {
            throw new IllegalArgumentException("Parent must have role " + model.role());
        }

        Set<String> visited = new HashSet<>();
        visited.add(model.name());
        GroupModel current = parent;
        while (current != null) {
            if (!visited.add(current.name())) throw new IllegalArgumentException("Group inheritance cycle detected");
            current = current.parentName() == null ? null : groups.get(current.parentName());
        }
    }

    private void validateDefaultSecondary(GroupModel model) {
        String secondaryName = model.defaultSecondaryName();
        if (secondaryName == null) return;
        if (model.role() != GroupRole.PRIMARY) {
            throw new IllegalArgumentException("Only PRIMARY groups may define a default secondary");
        }
        GroupModel secondary = groups.get(secondaryName);
        if (secondary == null) throw new IllegalArgumentException("Unknown secondary group: " + secondaryName);
        if (secondary.role() != GroupRole.SECONDARY) {
            throw new IllegalArgumentException("Default secondary must have role SECONDARY");
        }
    }
}
