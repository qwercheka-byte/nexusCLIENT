package dev.nexus.client.event.events;

/** Posted after the client joined a world/server (play phase started). */
public final class WorldJoinEvent {
    public static final WorldJoinEvent INSTANCE = new WorldJoinEvent();

    private WorldJoinEvent() {}
}
