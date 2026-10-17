package dev.nexus.client.event.events;

/** Raw keyboard input (GLFW codes). {@code action}: 0 release, 1 press, 2 repeat. */
public record KeyEvent(int key, int scancode, int action, int modifiers) {
    public boolean isPress() {
        return action == 1;
    }
}
