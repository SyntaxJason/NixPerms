package de.astranox.nixperms.paper;

import de.astranox.nixperms.core.NixPermsCore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Keeps Paper-only command classes out of the Spigot class-loading path while still
 * allowing one universal server JAR to use the native Paper API when available.
 */
final class PaperRuntime {

    private static final String BASIC_COMMAND = "io.papermc.paper.command.brigadier.BasicCommand";
    private static final String REGISTRAR = "de.astranox.nixperms.paper.PaperCommandRegistrar";

    private PaperRuntime() { }

    static boolean registerCommands(NixPermsBukkit plugin, NixPermsCore core) {
        ClassLoader loader = plugin.getClass().getClassLoader();
        if (!available(loader)) return false;

        try {
            Class<?> registrarType = Class.forName(REGISTRAR, true, loader);
            Method register = registrarType.getMethod("register", NixPermsBukkit.class, NixPermsCore.class);
            register.invoke(null, plugin, core);
            return true;
        } catch (InvocationTargetException error) {
            throw propagate(error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Paper command adapter could not be initialized", error);
        }
    }

    private static boolean available(ClassLoader loader) {
        try {
            Class.forName(BASIC_COMMAND, false, loader);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException("Paper command registration failed", error);
    }
}
