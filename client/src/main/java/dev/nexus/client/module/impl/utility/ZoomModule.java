package dev.nexus.client.module.impl.utility;

import dev.nexus.client.event.events.MouseScrollEvent;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.KeybindSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.ui.animation.SmoothValue;
import org.lwjgl.glfw.GLFW;

/** Hold-to-zoom with smooth animation and scroll adjustment. Applied through GameRendererMixin. */
public final class ZoomModule extends Module {
    private final KeybindSetting zoomKey = add(new KeybindSetting("zoom_key", "Zoom key", "Hold to zoom", GLFW.GLFW_KEY_C));
    private final NumberSetting factor = add(new NumberSetting("factor", "Zoom", "Initial magnification", 4, 1.5, 15, 0.5).suffix("x"));
    private final BooleanSetting smooth = add(new BooleanSetting("smooth", "Smooth", "Animate zooming in and out", true));
    private final BooleanSetting scroll = add(new BooleanSetting("scroll", "Scroll to adjust", "Mouse wheel changes the zoom while zooming", true));
    private final BooleanSetting cinematic = add(new BooleanSetting("cinematic", "Cinematic camera",
            "Enable smooth camera while zooming (restored afterwards)", false));

    private final SmoothValue multiplier = new SmoothValue(1f, 14f);
    private boolean zooming;
    private double level;
    private Boolean previousSmoothCamera;

    public ZoomModule() {
        super("zoom", "Zoom", "Hold a key to zoom in smoothly. Scroll while zooming to adjust.", Category.UTILITY, true);
        on(MouseScrollEvent.class, e -> {
            if (!zooming || !scroll.isOn() || e.amount == 0) return;
            level = Math.max(1.2, Math.min(50, level * (e.amount > 0 ? 1.18 : 1 / 1.18)));
            e.cancel();
        });
    }

    /** Called every frame from the FOV hook; returns the factor to multiply the FOV with. */
    public float fovMultiplier() {
        boolean active = isEnabled() && mc.currentScreen == null && mc.player != null && zoomKey.isDown();
        if (active != zooming) {
            zooming = active;
            if (active) {
                level = factor.get();
                if (cinematic.isOn()) {
                    previousSmoothCamera = mc.options.smoothCameraEnabled;
                    mc.options.smoothCameraEnabled = true;
                }
            } else {
                restoreCamera();
            }
        }
        float target = zooming ? (float) (1.0 / level) : 1f;
        if (!smooth.isOn()) {
            multiplier.snap(target);
            return target;
        }
        return multiplier.update(target);
    }

    private void restoreCamera() {
        if (previousSmoothCamera != null) {
            mc.options.smoothCameraEnabled = previousSmoothCamera;
            previousSmoothCamera = null;
        }
    }

    @Override
    protected void onDisable() {
        zooming = false;
        restoreCamera();
        multiplier.snap(1f);
    }
}
