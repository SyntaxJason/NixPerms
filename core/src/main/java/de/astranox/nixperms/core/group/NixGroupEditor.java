package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.group.IGroupEditor;
import de.astranox.nixperms.core.model.GroupModel;
import de.astranox.nixperms.core.model.MetaEntryModel;
import de.astranox.nixperms.core.permission.NixPermissionEditor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

final class NixGroupEditor extends NixPermissionEditor<NixGroupEditor> implements IGroupEditor {

    private final GroupModel original;
    private final List<MetaEntryModel> prefixes;
    private final List<MetaEntryModel> suffixes;
    private final Map<String, String> options;
    @Nullable private String parentName;
    @Nullable private String defaultSecondaryName;
    private int weight;

    NixGroupEditor(GroupModel original) {
        super(original.rules());
        this.original = original;
        this.parentName = original.parentName();
        this.defaultSecondaryName = original.defaultSecondaryName();
        this.weight = original.weight();
        this.prefixes = new ArrayList<>(original.prefixes());
        this.suffixes = new ArrayList<>(original.suffixes());
        this.options = new LinkedHashMap<>(original.options());
    }

    @Override public IGroupEditor parent(String groupName) { parentName = GroupModel.normalizeName(groupName); return this; }
    @Override public IGroupEditor clearParent() { parentName = null; return this; }
    @Override public IGroupEditor defaultSecondary(String groupName) { defaultSecondaryName = GroupModel.normalizeName(groupName); return this; }
    @Override public IGroupEditor clearDefaultSecondary() { defaultSecondaryName = null; return this; }
    @Override public IGroupEditor weight(int value) { weight = value; return this; }

    @Override
    public IGroupEditor option(String key, String value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Option key cannot be blank");
        if (value == null) throw new IllegalArgumentException("Option value cannot be null");
        options.put(key.trim().toLowerCase(Locale.ROOT), value);
        return this;
    }

    @Override public IGroupEditor removeOption(String key) { if (key != null) options.remove(key.trim().toLowerCase(Locale.ROOT)); return this; }
    @Override public IGroupEditor prefix(int priority, String value) { addMeta(prefixes, priority, value); return this; }
    @Override public IGroupEditor removePrefix(int priority, String value) { removeMeta(prefixes, priority, value); return this; }
    @Override public IGroupEditor suffix(int priority, String value) { addMeta(suffixes, priority, value); return this; }
    @Override public IGroupEditor removeSuffix(int priority, String value) { removeMeta(suffixes, priority, value); return this; }

    GroupModel build() {
        return new GroupModel(
                original.name(), original.role(), weight, parentName, defaultSecondaryName,
                buildRules(), sorted(prefixes), sorted(suffixes), options
        );
    }

    private void addMeta(List<MetaEntryModel> entries, int priority, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Meta value cannot be blank");
        entries.removeIf(entry -> entry.priority() == priority && entry.value().equals(value));
        entries.add(new MetaEntryModel(priority, value));
    }

    private void removeMeta(List<MetaEntryModel> entries, int priority, String value) {
        entries.removeIf(entry -> entry.priority() == priority && entry.value().equals(value));
    }

    private List<MetaEntryModel> sorted(List<MetaEntryModel> entries) {
        return entries.stream()
                .sorted((first, second) -> Integer.compare(second.priority(), first.priority()))
                .toList();
    }
}
