package de.astranox.nixperms.core.sync;

public record SyncMessage(long id, String serverId, String type, String payload) {}
