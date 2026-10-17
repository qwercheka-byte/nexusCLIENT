package dev.nexus.client.event.events;

/** Raw mouse button input (GLFW codes). {@code action}: 0 release, 1 press. */
public record MouseButtonEvent(int button, int action) {
    public boolean isPress() {
        return action == 1;
    }
}
