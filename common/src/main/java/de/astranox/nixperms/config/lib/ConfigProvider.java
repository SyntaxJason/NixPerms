package de.astranox.nixperms.config.lib;

import de.astranox.nixperms.logger.Logger;
import net.kyori.adventure.audience.Audience;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConfigProvider implements AutoCloseable {

    private static volatile ConfigProvider INSTANCE;

    private final Logger logger;
    private final Path dataDir;
    private final Map<Class<? extends NixConfigDaemon>, NixConfigDaemon> configs = new ConcurrentHashMap<>();

    private ConfigProvider(Path dataDir) {
        this.logger = Logger.getLogger();
        this.dataDir = dataDir;
        logInfo(mmInfo("ConfigProvider für " + mmPath(dataDir.toString())));
    }

    public static synchronized ConfigProvider initialize(Audience audience, File dataDirectory, String... basePackages) {
        Logger logger = Logger.getLogger(audience);

        if (INSTANCE != null) {
            logWarn(mmWarn("ConfigProvider.initialize() erneut aufgerufen – bestehende Instanz wird verwendet"));
            return INSTANCE;
        }

        logInfo(mmInfo("Initialisiere <#C7C7FF>ConfigProvider#C7C7FF> mit Paketen " + Arrays.toString(basePackages) + ""));
        ConfigProvider provider = new ConfigProvider(dataDirectory.toPath());

        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder().setScanners(Scanners.TypesAnnotated);
        for (String pkg : basePackages) {
            configurationBuilder.addUrls(ClasspathHelper.forPackage(pkg));
            configurationBuilder.filterInputsBy(new FilterBuilder().includePackage(pkg));
            logDebug("· Reflections: Paket " + mmCode(pkg));
        }

        Reflections refl = new Reflections(configurationBuilder);
        Set<Class<?>> types = new HashSet<>(refl.getTypesAnnotatedWith(NixConfiguration.class));
        logOk("Reflections: " + types.size() + " Konfiguration(en) gefunden");

        for (Class<?> type : types) {
            if (!NixConfigDaemon.class.isAssignableFrom(type)) {
                logWarn(mmWarn("Überspringe inkompatiblen Typ: " + mmCode(type.getName())));
                continue;
            }

            try {
                @SuppressWarnings("unchecked")
                Class<? extends NixConfigDaemon> daemonType = (Class<? extends NixConfigDaemon>) type;
                Constructor<? extends NixConfigDaemon> ctor = daemonType.getDeclaredConstructor();
                ctor.setAccessible(true);

                NixConfigDaemon inst = ctor.newInstance();
                NixConfiguration meta = daemonType.getAnnotation(NixConfiguration.class);

                logInfo(mmInfo("Binde <#B5E48C>" + daemonType.getSimpleName() + "#B5E48C> → " + mmHover(mmPath(meta.file()), "relativ zu dataFolder")));
                inst.bind(provider.dataDir, meta.file(), meta.debounceMs());

                if (meta.autoReload()) {
                    inst.start();
                    logOk("Daemon gestartet: " + mmCode(daemonType.getSimpleName()));
                }

                provider.configs.put(daemonType, inst);
            } catch (Throwable ex) {
                logError(mmErr("Fehler bei " + mmCode(type.getName()) + ": " + mmCode(ex.getMessage())));
            }
        }

        logOk("Bereit: " + provider.configs.size() + " Konfiguration(en) aktiv");
        return INSTANCE = provider;
    }

    public static ConfigProvider get() {
        return Objects.requireNonNull(INSTANCE, "ConfigProvider not initialized");
    }

    private static String mmInfo(String text) {
        return "» " + text + "";
    }

    private static String mmWarn(String text) {
        return "⚠ " + text + "";
    }

    private static String mmErr(String text) {
        return "✗ " + text + "";
    }

    private static String mmCode(String string) {
        return "" + string + "";
    }

    private static String mmPath(String path) {
        return "<#8AA2FF>" + path + "#8AA2FF>";
    }

    private static String mmHover(String base, String hover) {
        return "" + base + "";
    }

    private static void logInfo(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.info(message);
    }

    private static void logWarn(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.warn(message);
    }

    private static void logError(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.error(message);
    }

    private static void logOk(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.info("✓ " + message);
    }

    private static void logDebug(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.debug(message);
    }

    public <T extends NixConfigDaemon> T get(Class<T> type) {
        NixConfigDaemon inst = configs.get(type);
        if (inst == null) {
            logWarn(mmWarn("get(): Keine Instanz für " + mmCode(type.getName())));
            return null;
        }
        return type.cast(inst);
    }

    public Logger getLogger() {
        return logger;
    }

    public boolean reloadAll() {
        logInfo(mmInfo("Reload aller Konfigurationen gestartet"));
        AtomicInteger ok = new AtomicInteger();

        configs.values().forEach(config -> {
            try {
                config.run();
                logOk("Reload ok -> " + mmCode(config.getClass().getSimpleName()));
                ok.getAndIncrement();
            } catch (Throwable ex) {
                logError(mmErr("Reload Fehler in " + mmCode(config.getClass().getSimpleName()) + ": " + mmCode(ex.getMessage())));
            }
        });

        return ok.intValue() == configs.size();
    }

    @Override
    public void close() {
        logInfo(mmInfo("Schließe Provider, stoppe " + configs.size() + " Daemon(s)"));

        configs.values().forEach(config -> {
            try {
                config.close();
                logOk("Gestoppt: " + mmCode(config.getClass().getSimpleName()));
            } catch (Exception ex) {
                logError(mmErr("Stop-Fehler in " + mmCode(config.getClass().getSimpleName()) + ": " + mmCode(ex.getMessage())));
            }
        });

        configs.clear();
        INSTANCE = null;
        logOk("ConfigProvider geschlossen");
    }
}
