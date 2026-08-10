package de.astranox.nixperms.core.model;

public record MetaEntryModel(int priority, String value) {
    public MetaEntryModel {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Meta value cannot be blank");
        if (value.length() > 512) throw new IllegalArgumentException("Meta value is longer than 512 characters");
    }
}
