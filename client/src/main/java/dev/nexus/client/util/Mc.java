package dev.nexus.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.SystemKeycodes;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/** Tiny accessor to keep call sites short. */
public final class Mc {
    private Mc() {}

    public static MinecraftClient get() {
        return MinecraftClient.getInstance();
    }

    public static boolean inWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null;
    }

    /** Physical shift state (for mouse handlers that receive no modifier bits). */
    public static boolean shiftDown() {
        return MinecraftClient.getInstance().isShiftPressed();
    }

    /** Ctrl, or Cmd on macOS – the same rule vanilla uses for text shortcuts. */
    public static boolean ctrlDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!SystemKeycodes.IS_MAC_OS) return mc.isCtrlPressed();
        Window w = mc.getWindow();
        return InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_LEFT_SUPER) || InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_RIGHT_SUPER);
    }

    /** Ctrl/Cmd according to GLFW modifier bits of a key event. */
    public static boolean ctrl(int modifiers) {
        return (modifiers & SystemKeycodes.CTRL_MOD) != 0;
    }

    public static boolean shift(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }
}
