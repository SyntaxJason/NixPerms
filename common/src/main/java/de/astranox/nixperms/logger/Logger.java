package de.astranox.nixperms.logger;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Logger {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String PREFIX_MM = "ᴠᴇꜱᴘᴇʀɪᴀ.ɴᴇᴛ";
    private static final String SEP_MM = " | ";
    private static final String L_INFO = "[INFO]";
    private static final String L_WARN = "[WARN]";
    private static final String L_ERROR = "[ERROR]";
    private static final String L_DEBUG = "[DEBUG]";
    private static Logger instance;
    private final Audience out;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile boolean debugEnabled = false;
    private Logger(Audience out) {
        this.out = out;
        instance = this;
    }

    public static Logger getLogger(Audience audience) {
        if (instance != null) return instance;
        return new Logger(audience);
    }

    public static Logger getLogger() {
        return instance;
    }

    public Logger debugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        return this;
    }

    private String levelTag(Level level) {
        return switch (level) {
            case INFO -> L_INFO;
            case WARN -> L_WARN;
            case ERROR -> L_ERROR;
            case DEBUG -> L_DEBUG;
        };
    }

    public void log(Level level, String messageMiniMessage) {
        if (level == Level.DEBUG && !this.debugEnabled) return;

        String now = LocalDateTime.now().format(TIME_FMT);

        TagResolver resolver = TagResolver.builder()
                .tag("prefix", Tag.selfClosingInserting(mm.deserialize(PREFIX_MM)))
                .tag("level", Tag.selfClosingInserting(mm.deserialize(levelTag(level))))
                .tag("time", Tag.selfClosingInserting(Component.text(now)))
                .tag("message", Tag.selfClosingInserting(mm.deserialize(messageMiniMessage)))
                .build();

        String pattern = "[] " + "" + SEP_MM + " ";
        Component finalMsg = mm.deserialize(pattern, resolver);
        out.sendMessage(finalMsg);
    }

    public void logPlain(Level level, String plainMessage) {
        if (level == Level.DEBUG && !this.debugEnabled) return;

        String now = LocalDateTime.now().format(TIME_FMT);

        TagResolver resolver = TagResolver.builder()
                .tag("prefix", Tag.selfClosingInserting(mm.deserialize(PREFIX_MM)))
                .tag("level", Tag.selfClosingInserting(mm.deserialize(levelTag(level))))
                .tag("time", Tag.selfClosingInserting(Component.text(now)))
                .tag("message", Tag.selfClosingInserting(Component.text(plainMessage)))
                .build();

        String pattern = "[] " + "" + SEP_MM + " ";
        Component finalMsg = mm.deserialize(pattern, resolver);
        out.sendMessage(finalMsg);
    }

    public void info(String m) {
        log(Level.INFO, m);
    }

    public void warn(String m) {
        log(Level.WARN, m);
    }

    public void error(String m) {
        log(Level.ERROR, m);
    }

    public void error(String m, Throwable throwable) {
        log(Level.ERROR, String.format(m, throwable.getMessage()));
    }

    public void debug(String m) {
        log(Level.DEBUG, m);
    }

    public enum Level {INFO, WARN, ERROR, DEBUG}
}
