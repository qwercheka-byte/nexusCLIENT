package dev.nexus.client.ui.component;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Supplier;

/** Square, icon-only button with hover highlight and tooltip. */
public class IconButton extends Component {
    private Supplier<Icon> icon;
    private final Runnable action;
    private String tooltip;
    private int activeColor = 0;
    private boolean pressed;

    public IconButton(Icon icon, String tooltip, Runnable action) {
        this.icon = () -> icon;
        this.tooltip = tooltip;
        this.action = action;
        this.width = this.height = 18;
    }

    public IconButton dynamicIcon(Supplier<Icon> icon) {
        this.icon = icon;
        return this;
    }

    /** Hover colour of the glyph (defaults to text colour). */
    public IconButton activeColor(int color) {
        this.activeColor = color;
        return this;
    }

    public IconButton tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float h = updateHover(mx, my);
        Render2D.pushAlpha(enabled ? 1f : 0.35f);
        if (h > 0.01f) Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS_SMALL, ColorUtil.withAlpha(0xFFFFFFFF, (int) (h * 18)));
        float size = Math.min(width, height) * 0.58f;
        int c = ColorUtil.lerp(Theme.TEXT_MUTED, activeColor != 0 ? activeColor : Theme.TEXT, h);
        IconRender.drawCentered(ctx, icon.get(), x + width / 2f, y + height / 2f + (pressed ? 0.5f : 0f), size, c);
        Render2D.popAlpha();
        if (tooltip != null && isHovered(mx, my)) UiContext.tooltip(tooltip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isHovered(mx, my)) return false;
        pressed = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!pressed) return false;
        pressed = false;
        if (isHovered(mx, my)) {
            Button.playClick();
            action.run();
        }
        return true;
    }
}
