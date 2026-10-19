package dev.nexus.client.event.events;

/** Posted at the end of every client tick (20 TPS). Singleton – carries no data. */
public final class TickEvent {
    public static final TickEvent INSTANCE = new TickEvent();

    private TickEvent() {}
}
