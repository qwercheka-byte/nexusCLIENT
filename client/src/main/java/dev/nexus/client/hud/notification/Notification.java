package dev.nexus.client.hud.notification;

import dev.nexus.client.render.Icon;
import dev.nexus.client.ui.animation.Animation;
import dev.nexus.client.ui.animation.Easing;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;

/** A toast message. The display timer starts when it is first drawn. */
public final class Notification {
    public enum Type {
        INFO(Icon.INFO), SUCCESS(Icon.CHECK), WARNING(Icon.INFO), ERROR(Icon.CLOSE), MODULE_ON(Icon.POWER), MODULE_OFF(Icon.POWER);

        public final Icon icon;

        Type(Icon icon) {
            this.icon = icon;
        }

        public int color() {
            return switch (this) {
                case SUCCESS, MODULE_ON -> Theme.SUCCESS;
                case WARNING -> Theme.WARNING;
                case ERROR -> Theme.DANGER;
                case MODULE_OFF -> Theme.TEXT_DIM;
                default -> Theme.accent();
            };
        }
    }

    final String title;
    final String message;
    final Type type;
    final long durationMs;
    long shownAt = -1;
    final Animation visibility = new Animation(0f, 260, Easing.OUT_CUBIC);
    final SmoothValue slot = new SmoothValue(-1f, 14f);
    boolean leaving;
    /** Ellipsized display strings, computed once on first draw. */
    String titleShown, messageShown;

    Notification(String title, String message, Type type, long durationMs) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.durationMs = durationMs;
    }
}
