package dev.nexus.client.module.impl.interfaces;

import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.NumberSetting;

/**
 * Configures toast notifications. While enabled, hotkey toggles show a toast.
 * System messages (profile actions, config errors) are always shown using these settings.
 */
public final class NotificationsModule extends Module {
    private final ModeSetting position = add(new ModeSetting("position", "Position", "Screen corner for notifications",
            "Bottom Right", "Bottom Right", "Top Right", "Bottom Left", "Top Left"));
    private final NumberSetting duration = add(new NumberSetting("duration", "Duration", "How long a toast stays visible",
            2.5, 1, 8, 0.5).suffix("s"));
    private final BooleanSetting toggles = add(new BooleanSetting("toggles", "Module toggles",
            "Notify when a module is toggled with its hotkey", true));

    public NotificationsModule() {
        super("notifications", "Notifications", "Toasts for hotkey toggles. System messages always use these settings.",
                Category.INTERFACE, true);
    }

    public String position() {
        return position.get();
    }

    public long durationMs() {
        return Math.round(duration.get() * 1000);
    }

    public boolean showToggles() {
        return toggles.isOn();
    }

    @Override
    public boolean showInModuleList() {
        return false;
    }
}
