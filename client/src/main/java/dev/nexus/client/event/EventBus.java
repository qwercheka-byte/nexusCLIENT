package dev.nexus.client.event;

import dev.nexus.client.NexusClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal synchronous event bus used on the client (render/main) thread.
 * <p>
 * Listener arrays are copy-on-write: subscribing/unsubscribing replaces the array, so dispatch
 * never allocates and handlers may safely (un)subscribe while an event is being posted.
 */
public final class EventBus {
    private static final Consumer<?>[] EMPTY = new Consumer<?>[0];
    private final Map<Class<?>, Consumer<?>[]> listeners = new HashMap<>();

    public synchronized <T> void subscribe(Class<T> type, Consumer<? super T> listener) {
        Consumer<?>[] current = listeners.getOrDefault(type, EMPTY);
        for (Consumer<?> c : current) if (c == listener) return;
        Consumer<?>[] next = Arrays.copyOf(current, current.length + 1);
        next[current.length] = listener;
        listeners.put(type, next);
    }

    public synchronized <T> void unsubscribe(Class<T> type, Consumer<? super T> listener) {
        Consumer<?>[] current = listeners.get(type);
        if (current == null) return;
        int idx = -1;
        for (int i = 0; i < current.length; i++) {
            if (current[i] == listener) { idx = i; break; }
        }
        if (idx < 0) return;
        if (current.length == 1) {
            listeners.remove(type);
            return;
        }
        Consumer<?>[] next = new Consumer<?>[current.length - 1];
        System.arraycopy(current, 0, next, 0, idx);
        System.arraycopy(current, idx + 1, next, idx, current.length - idx - 1);
        listeners.put(type, next);
    }

    @SuppressWarnings("unchecked")
    public <T> T post(T event) {
        Consumer<?>[] current;
        synchronized (this) {
            current = listeners.get(event.getClass());
        }
        if (current == null) return event;
        for (Consumer<?> c : current) {
            try {
                ((Consumer<T>) c).accept(event);
            } catch (Throwable t) {
                NexusClient.LOGGER.error("Unhandled exception in listener for {}", event.getClass().getSimpleName(), t);
            }
        }
        return event;
    }

    public synchronized boolean hasListeners(Class<?> type) {
        return listeners.containsKey(type);
    }
}
