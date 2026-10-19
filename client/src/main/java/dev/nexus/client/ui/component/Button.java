package dev.nexus.client.ui.component;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.util.function.Supplier;

/** Text button with optional icon in one of four styles. */
public class Button extends Component {
    public enum Style { PRIMARY, SECONDARY, GHOST, DANGER }

    private Supplier<String> label;
    private final Icon icon;
    private final Style style;
    private final Runnable action;
    private final SmoothValue press = new SmoothValue(0f, 26f);
    private String tooltip;
    private boolean pressed;

    public Button(String label, Icon icon, Style style, Runnable action) {
        this.label = () -> label;
        this.icon = icon;
        this.style = style;
        this.action = action;
        this.height = 20;
    }

    public Button dynamicLabel(Supplier<String> label) {
        this.label = label;
        return this;
    }

    public Button tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Natural width for the label and icon. */
    public float naturalWidth() {
        String l = label.get();
        float w = l.isEmpty() ? 0 : TextRender.width(l);
        if (icon != null) w += l.isEmpty() ? 10 : 14;
        return w + 20;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float h = updateHover(mx, my);
        float p = press.update(pressed ? 1f : 0f);
        float a = enabled ? 1f : 0.4f;
        Render2D.pushAlpha(a);
        float inset = p * 0.6f;
        float bx = x + inset, by = y + inset, bw = width - inset * 2, bh = height - inset * 2;
        int bg, fg, border = 0;
        switch (style) {
            case PRIMARY -> {
                bg = ColorUtil.lerp(Theme.accent(), Theme.accentBright(), h * 0.5f);
                fg = Theme.onAccent();
            }
            case DANGER -> {
                bg = ColorUtil.lerp(0x26F87171, 0x40F87171, h);
                fg = ColorUtil.lerp(0xFFFCA5A5, 0xFFFFFFFF, h * 0.3f);
                border = 0x40F87171;
            }
            case GHOST -> {
                bg = ColorUtil.withAlpha(0xFFFFFFFF, (int) (h * 14));
                fg = ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, h);
            }
            default -> {
                bg = ColorUtil.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, h);
                fg = Theme.TEXT;
                border = Theme.BORDER;
            }
        }
        Render2D.roundedRect(ctx, bx, by, bw, bh, Theme.RADIUS_SMALL, bg);
        if (border != 0) Render2D.roundedOutline(ctx, bx, by, bw, bh, Theme.RADIUS_SMALL, 1f, border);
        String l = label.get();
        float contentW = (l.isEmpty() ? 0 : TextRender.width(l)) + (icon != null ? (l.isEmpty() ? 10 : 14) : 0);
        float cx = bx + (bw - contentW) / 2f;
        if (icon != null) {
            IconRender.draw(ctx, icon, cx, by + (bh - 10) / 2f, 10, fg);
            cx += 14;
        }
        if (!l.isEmpty()) TextRender.draw(ctx, l, cx, TextRender.centerY(by, bh), fg);
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
        if (isHovered(mx, my) && enabled) {
            playClick();
            action.run();
        }
        return true;
    }

    public static void playClick() {
        net.minecraft.client.MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
