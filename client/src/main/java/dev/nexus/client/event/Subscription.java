package dev.nexus.client.event;

import java.util.function.Consumer;

/** A listener bound to an event type; used by modules to (un)register handlers with their lifecycle. */
public record Subscription<T>(Class<T> type, Consumer<? super T> listener) {
    public void register(EventBus bus) {
        bus.subscribe(type, listener);
    }

    public void unregister(EventBus bus) {
        bus.unsubscribe(type, listener);
    }
}
