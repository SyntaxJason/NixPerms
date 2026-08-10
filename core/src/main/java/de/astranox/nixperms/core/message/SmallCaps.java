package de.astranox.nixperms.core.message;

public final class SmallCaps {

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {}

    public static String convert(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            int index = NORMAL.indexOf(Character.toLowerCase(c));
            sb.append(index != -1 ? SMALL.charAt(index) : c);
        }
        return sb.toString();
    }
}
