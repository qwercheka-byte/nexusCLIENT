package dev.nexus.client.module.impl.performance;

import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.setting.NumberSetting;

/** Caps the frame rate while the game window is not focused (applied in InactivityFpsLimiterMixin). */
public final class UnfocusedFpsModule extends Module {
    private final NumberSetting limit = add(new NumberSetting("limit", "FPS limit", "Maximum FPS while the window is unfocused",
            15, 1, 60, 1).suffix(" FPS"));

    public UnfocusedFpsModule() {
        super("unfocused_fps", "Unfocused FPS", "Saves power and GPU time by limiting FPS when the game is in the background.",
                Category.PERFORMANCE);
    }

    /** Limit to apply right now, or 0 for none. */
    public int currentLimit() {
        return mc.isWindowFocused() ? 0 : limit.getInt();
    }
}
