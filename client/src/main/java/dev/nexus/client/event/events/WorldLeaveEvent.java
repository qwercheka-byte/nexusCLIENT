package dev.nexus.client.event.events;

/** Posted when the client disconnects from a world/server. */
public final class WorldLeaveEvent {
    public static final WorldLeaveEvent INSTANCE = new WorldLeaveEvent();

    private WorldLeaveEvent() {}
}
