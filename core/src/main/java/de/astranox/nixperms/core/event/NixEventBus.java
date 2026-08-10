package de.astranox.nixperms.core.event;

import de.astranox.nixperms.api.event.IEventBus;
import de.astranox.nixperms.api.event.INixEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class NixEventBus implements IEventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(NixEventBus.class);

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<INixEvent>>> listeners =
            new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends INixEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        if (eventType == null) throw new IllegalArgumentException("Event type cannot be null");
        if (listener == null) throw new IllegalArgumentException("Event listener cannot be null");
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>())
                .add((Consumer<INixEvent>) listener);
    }

    @Override
    public <T extends INixEvent> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<INixEvent>> registered = listeners.get(eventType);
        if (registered == null) return;
        registered.remove(listener);
        if (registered.isEmpty()) listeners.remove(eventType, registered);
    }

    @Override
    public void post(INixEvent event) {
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        listeners.forEach((type, registered) -> {
            if (!type.isInstance(event)) return;
            registered.forEach(listener -> dispatch(listener, event));
        });
    }

    private void dispatch(Consumer<INixEvent> listener, INixEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("NixPerms event listener failed for {}", event.getClass().getSimpleName(), exception);
        }
    }
}
