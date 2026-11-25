package dev.nexus.recovery;

import dev.nexus.client.hud.RShiftHud;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.ui.animation.AnimationSettings;
import net.minecraft.class_310;
import net.minecraft.class_332;
import org.lwjgl.glfw.GLFW;

/** Adapter is compiled against minimal intermediary-name stubs, never packaged with the stubs. */
public final class HudAdapter {
    private static final HudMotion MOTION = new HudMotion();
    public static void frame(class_332 g, boolean chat, int sw, int sh,
                             float[][] target, float scale, float offsetX, float offsetY) {
        class_310 mc = class_310.method_1551();
        int width = mc.method_22683().method_4480();
        int height = mc.method_22683().method_4507();
        if (width <= 0 || height <= 0 || scale <= 0) return;
        double mx = (mc.field_1729.method_1603() * sw / width - offsetX) / scale;
        double my = (mc.field_1729.method_1604() * sh / height - offsetY) / scale;
        boolean down = GLFW.glfwGetMouseButton(mc.method_22683().method_4490(), 0) == 1;
        MOTION.step(target, chat, down, mx, my, System.nanoTime(), AnimationSettings.speed(), AnimationSettings.instant());
        if (MOTION.takeDirty()) RShiftHud.nexus$save();
        if (chat) for (int zone : MOTION.active()) {
            float[] box = MOTION.box(zone);
            float x = offsetX + (box[0] + MOTION.position(zone, 0)) * scale;
            float y = offsetY + (box[1] + MOTION.position(zone, 1)) * scale;
            Render2D.roundedOutline(g, x - 2, y - 2, box[2] * scale + 4, box[3] * scale + 4,
                    3, 0.8f * Math.max(1, scale), -863142913);
        }
    }
    public static float position(int zone, int axis) { return MOTION.position(zone, axis); }
}
