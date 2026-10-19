package dev.nexus.client.module;

import dev.nexus.client.util.Lang;
import dev.nexus.client.render.Icon;

public enum Category {
    VISUALS("Visuals", Icon.EYE),
    HUD("HUD", Icon.MONITOR),
    INTERFACE("Interface", Icon.LAYOUT),
    UTILITY("Utility", Icon.WRENCH),
    PERFORMANCE("Performance", Icon.BOLT);

    private final String displayName;
    private final Icon icon;

    Category(String displayName, Icon icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return Lang.tr(displayName);
    }

    public Icon getIcon() {
        return icon;
    }
}
