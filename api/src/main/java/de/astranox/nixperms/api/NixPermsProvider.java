package de.astranox.nixperms.api;

import java.util.concurrent.atomic.AtomicReference;

/** Platform-neutral API provider for plugins that depend on NixPerms. */
public final class NixPermsProvider {

    private static final AtomicReference<INixPermsAPI> INSTANCE = new AtomicReference<>();

    private NixPermsProvider() { }

    public static INixPermsAPI get() {
        INixPermsAPI api = INSTANCE.get();
        if (api == null) throw new IllegalStateException("NixPerms is not loaded");
        return api;
    }

    public static boolean available() {
        return INSTANCE.get() != null;
    }

    public static void register(INixPermsAPI api) {
        if (api == null) throw new IllegalArgumentException("API cannot be null");
        if (!INSTANCE.compareAndSet(null, api)) {
            throw new IllegalStateException("A NixPerms API instance is already registered");
        }
    }

    public static void unregister(INixPermsAPI api) {
        if (api == null) return;
        INSTANCE.compareAndSet(api, null);
    }
}
