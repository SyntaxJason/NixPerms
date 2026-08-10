package de.astranox.nixperms.core.user;

import de.astranox.nixperms.api.user.IUserEditor;
import de.astranox.nixperms.core.model.GroupModel;
import de.astranox.nixperms.core.model.UserModel;
import de.astranox.nixperms.core.permission.NixPermissionEditor;
import org.jetbrains.annotations.Nullable;

final class NixUserEditor extends NixPermissionEditor<NixUserEditor> implements IUserEditor {

    private final UserModel original;
    @Nullable private String name;
    private String primaryGroupName;
    @Nullable private String secondaryGroupName;

    NixUserEditor(UserModel original) {
        super(original.rules());
        this.original = original;
        this.name = original.name();
        this.primaryGroupName = original.primaryGroupName();
        this.secondaryGroupName = original.secondaryGroupName();
    }

    @Override public IUserEditor name(String value) { name = value; return this; }
    @Override public IUserEditor primary(String groupName) { primaryGroupName = GroupModel.normalizeName(groupName); return this; }
    @Override public IUserEditor secondary(String groupName) { secondaryGroupName = GroupModel.normalizeName(groupName); return this; }
    @Override public IUserEditor clearSecondary() { secondaryGroupName = null; return this; }
    UserModel build() {
        return new UserModel(
                original.uniqueId(), name, primaryGroupName, secondaryGroupName, buildRules()
        );
    }
}
