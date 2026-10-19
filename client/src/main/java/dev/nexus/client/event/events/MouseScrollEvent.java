package dev.nexus.client.event.events;

/** Mouse wheel input while no screen is open. Listeners may cancel to stop vanilla handling (hotbar scroll). */
public final class MouseScrollEvent {
    public static final MouseScrollEvent INSTANCE = new MouseScrollEvent();

    public double amount;
    private boolean cancelled;

    private MouseScrollEvent() {}

    public MouseScrollEvent set(double amount) {
        this.amount = amount;
        this.cancelled = false;
        return this;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
