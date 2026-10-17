package dev.nexus.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nexus.client.util.KeyNames;
import dev.nexus.client.util.Mc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * A key or mouse button.
 * Keyboard keys are stored as GLFW key codes, mouse buttons as {@code MOUSE_BASE + button}.
 */
public final class KeybindSetting extends Setting<Integer> {
    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN; // -1
    public static final int MOUSE_BASE = 1000;
    private String cachedName;
    private int cachedFor = Integer.MIN_VALUE;

    public KeybindSetting(String id, String name, String description, int defaultValue) {
        super(id, name, description, defaultValue);
    }

    @Override
    protected Integer validate(Integer candidate) {
        if (candidate == null) return null;
        int c = candidate;
        if (c == NONE) return c;
        if (c >= MOUSE_BASE && c <= MOUSE_BASE + GLFW.GLFW_MOUSE_BUTTON_LAST) return c;
        if (c >= GLFW.GLFW_KEY_SPACE && c <= GLFW.GLFW_KEY_LAST) return c;
        return null;
    }

    public boolean isBound() {
        return value != NONE;
    }

    public int getKey() {
        return value;
    }

    public boolean matchesKey(int glfwKey) {
        return value != NONE && value < MOUSE_BASE && value == glfwKey;
    }

    public boolean matchesMouse(int button) {
        return value == MOUSE_BASE + button;
    }

    /** Polls the physical state of the bound key (true while held). */
    public boolean isDown() {
        if (value == NONE) return false;
        MinecraftClient mc = Mc.get();
        if (value >= MOUSE_BASE) return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), value - MOUSE_BASE) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(mc.getWindow(), value);
    }

    /** Display name, cached until the binding changes. */
    public String keyName() {
        if (cachedFor != value || cachedName == null) {
            cachedName = KeyNames.name(value);
            cachedFor = value;
        }
        return cachedName;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsInt());
    }
}
