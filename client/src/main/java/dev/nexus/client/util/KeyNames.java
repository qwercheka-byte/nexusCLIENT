package dev.nexus.client.util;

import dev.nexus.client.setting.KeybindSetting;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Human readable names for keyboard keys and mouse buttons used by key binds. */
public final class KeyNames {
    private KeyNames() {}

    public static String name(int code) {
        if (code == KeybindSetting.NONE) return "None";
        if (code >= KeybindSetting.MOUSE_BASE) {
            int button = code - KeybindSetting.MOUSE_BASE;
            return switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
                default -> "Mouse " + (button + 1);
            };
        }
        return switch (code) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            default -> {
                String s = InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(code, 0, 0)).getLocalizedText().getString();
                yield s.length() == 1 ? s.toUpperCase(java.util.Locale.ROOT) : s;
            }
        };
    }
}
