package de.astranox.nixperms.api.event;

import java.util.function.Consumer;

public interface IEventBus {
    <T extends INixEvent> void subscribe(Class<T> eventType, Consumer<T> listener);
    <T extends INixEvent> void unsubscribe(Class<T> eventType, Consumer<T> listener);
    void post(INixEvent event);
}
