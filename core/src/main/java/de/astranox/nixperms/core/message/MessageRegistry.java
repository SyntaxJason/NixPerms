package de.astranox.nixperms.core.message;

import de.astranox.nixperms.api.annotation.message.Locale;
import de.astranox.nixperms.api.annotation.message.Message;
import de.astranox.nixperms.api.annotation.message.MessagePrefix;
import de.astranox.nixperms.api.message.MessageProvider;
import de.astranox.nixperms.api.platform.Platform;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageRegistry {

    private static final String GLOBAL = "global";

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, String>>> store =
            new ConcurrentHashMap<>();
    private volatile String defaultLocale = "en_us";
    private volatile String globalPrefix = "";

    public void register(Class<?> messageClass) {
        Locale localeAnnotation = messageClass.getAnnotation(Locale.class);
        String locale = localeAnnotation != null
                ? localeAnnotation.value().toLowerCase(java.util.Locale.ROOT)
                : defaultLocale;
        MessagePrefix prefixAnnotation = messageClass.getAnnotation(MessagePrefix.class);
        String classPrefix = prefixAnnotation != null ? prefixAnnotation.value() : "";
        if (prefixAnnotation != null && prefixAnnotation.global()) globalPrefix = prefixAnnotation.value();
        for (Field field : messageClass.getDeclaredFields()) {
            Message annotation = field.getAnnotation(Message.class);
            if (annotation == null || !MessageProvider.class.isAssignableFrom(field.getType())) continue;
            registerField(field, annotation, locale, classPrefix);
        }
    }

    public void set(String locale, String platformKey, String id, String raw) {
        store.computeIfAbsent(locale.toLowerCase(java.util.Locale.ROOT), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(platformKey, ignored -> new ConcurrentHashMap<>())
                .put(id, raw);
    }

    public String getRaw(String locale, Platform platform, String id) {
        String platformKey = platform.name().toLowerCase(java.util.Locale.ROOT);
        String found = lookup(locale, platformKey, id);
        if (found != null) return found;
        found = lookup(locale, GLOBAL, id);
        if (found != null) return found;
        found = lookup(defaultLocale, platformKey, id);
        if (found != null) return found;
        found = lookup(defaultLocale, GLOBAL, id);
        return found != null ? found : id;
    }

    public void setDefaultLocale(String locale) {
        if (locale == null || locale.isBlank()) throw new IllegalArgumentException("Locale cannot be blank");
        this.defaultLocale = locale.toLowerCase(java.util.Locale.ROOT);
    }

    public String globalPrefix() {
        return globalPrefix;
    }

    private void registerField(Field field, Message annotation, String locale, String classPrefix) {
        try {
            field.setAccessible(true);
            MessageProvider provider = (MessageProvider) field.get(null);
            if (provider == null) return;
            String prefix = classPrefix.isEmpty() ? globalPrefix : classPrefix;
            String raw = annotation.prefix() ? prefix + provider.raw() : provider.raw();
            if (annotation.platforms().length == 0) {
                set(locale, GLOBAL, annotation.value(), raw);
                return;
            }
            for (Platform platform : annotation.platforms()) {
                set(locale, platform.name().toLowerCase(java.util.Locale.ROOT), annotation.value(), raw);
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not register message field: " + field.getName(), exception);
        }
    }

    private String lookup(String locale, String platformKey, String id) {
        Map<String, ConcurrentHashMap<String, String>> byLocale =
                store.get(locale.toLowerCase(java.util.Locale.ROOT));
        if (byLocale == null) return null;
        Map<String, String> byPlatform = byLocale.get(platformKey);
        if (byPlatform == null) return null;
        return byPlatform.get(id);
    }
}
