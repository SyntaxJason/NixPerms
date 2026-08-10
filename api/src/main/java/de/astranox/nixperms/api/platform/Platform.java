package de.astranox.nixperms.api.platform;

public enum Platform {
    BUKKIT,
    VELOCITY,
    BUNGEE,
    CONSOLE,
    WEB;

    public String commandPrefix() {
        return switch (this) {
            case BUKKIT, VELOCITY, BUNGEE -> "/";
            case CONSOLE, WEB -> "";
        };
    }
}
