package de.astranox.nixperms.core.message;

public final class NixPermsStyle {

    private NixPermsStyle() {}

    public static final String PREFIX = "<dark_gray>「<gradient:#FFFFFF:#00D4FF>ɴɪxᴘᴇʀᴍs</gradient><dark_gray>」 ";
    public static final String PRIMARY = "<#00D4FF>";
    public static final String SUCCESS = "<#4ADE80>";
    public static final String ERROR = "<#FF4444>";
    public static final String MUTED = "<#8B8B8B>";
    public static final String WHITE = "<white>";

    public static String success(String text) { return SUCCESS + text + WHITE; }
    public static String error(String text) { return ERROR + text + WHITE; }
    public static String primary(String text) { return PRIMARY + text + WHITE; }
    public static String muted(String text) { return MUTED + text + WHITE; }
}
