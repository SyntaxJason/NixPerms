package de.astranox.nixperms.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class SmallCaps {

    private static final Map<Integer, String> MAP = new HashMap<>();
    private static final String DIAERESIS = "\u0308";

    static {
        MAP.put((int) 'a', "ᴀ");
        MAP.put((int) 'b', "ʙ");
        MAP.put((int) 'c', "ᴄ");
        MAP.put((int) 'd', "ᴅ");
        MAP.put((int) 'e', "ᴇ");
        MAP.put((int) 'f', "ꜰ");
        MAP.put((int) 'g', "ɢ");
        MAP.put((int) 'h', "ʜ");
        MAP.put((int) 'i', "ɪ");
        MAP.put((int) 'j', "ᴊ");
        MAP.put((int) 'k', "ᴋ");
        MAP.put((int) 'l', "ʟ");
        MAP.put((int) 'm', "ᴍ");
        MAP.put((int) 'n', "ɴ");
        MAP.put((int) 'o', "ᴏ");
        MAP.put((int) 'p', "ᴘ");
        MAP.put((int) 'q', "ꞯ");
        MAP.put((int) 'r', "ʀ");

        MAP.put((int) 's', "s");
        MAP.put((int) 't', "ᴛ");
        MAP.put((int) 'u', "ᴜ");
        MAP.put((int) 'v', "ᴠ");
        MAP.put((int) 'w', "ᴡ");
        MAP.put((int) 'x', "x");
        MAP.put((int) 'y', "ʏ");
        MAP.put((int) 'z', "ᴢ");
        for (char c = 'A'; c <= 'Z'; c++) {
            MAP.put((int) c, MAP.get((int) Character.toLowerCase(c)));
        }
    }

    private SmallCaps() {
    }

    public static String toSmallCaps(String input) {
        return toSmallCaps(input, UmlautMode.KEEP).asComponent().insertion();
    }

    public static @Nullable ComponentLike toSmallCaps(String input, UmlautMode umlauts) {
        if (input == null || input.isEmpty()) return MiniMessage.miniMessage().deserialize("");
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            int next = Character.charCount(cp);

            if (umlauts != UmlautMode.KEEP) {
                if (cp == 'ä') {
                    out.append(umlauts == UmlautMode.COMPOSE ? "ᴀ" + DIAERESIS : "ae");
                    i += next;
                    continue;
                }
                if (cp == 'ö') {
                    out.append(umlauts == UmlautMode.COMPOSE ? "ᴏ" + DIAERESIS : "oe");
                    i += next;
                    continue;
                }
                if (cp == 'ü') {
                    out.append(umlauts == UmlautMode.COMPOSE ? "ᴜ" + DIAERESIS : "ue");
                    i += next;
                    continue;
                }
                if (cp == 'Ä') {
                    out.append(umlauts == UmlautMode.COMPOSE ? "ᴀ" + DIAERESIS : "Ae");
                    i += next;
                    continue;
                }
                if (cp == 'Ö') {
                    out.append(umlauts == UmlautMode.COMPOSE ? "ᴏ" + DIAERESIS : "Oe");
                    i += next;
                    continue;
                }
                if (cp == 'Ü') {
                    out.append(umlauts == UmlautMode.COMPOSE ? "ᴜ" + DIAERESIS : "Ue");
                    i += next;
                    continue;
                }
            }

            String mapped = MAP.get(cp);
            if (mapped != null) {
                out.append(mapped);
                i += next;
                continue;
            }
            out.appendCodePoint(cp);
            i += next;
        }
        return MiniMessage.miniMessage().deserialize(out.toString());
    }

    public static Component toSmallCaps(Component component, UmlautMode umlauts) {
        if (component == null) return null;
        return component.replaceText(TextReplacementConfig.builder()
                .match(Pattern.compile("(?s).+"))
                .replacement((mr, b) -> toSmallCaps(mr.group(), umlauts))
                .build());
    }

    public enum UmlautMode {
        KEEP,
        AE_OE_UE,
        COMPOSE
    }
}

