package dev.nexus.client.ui.component;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.function.IntSupplier;

/** Row of round colour swatches for the accent preset setting; the last one is "Custom". */
public class AccentSwatches extends Component {
    private static final float SIZE = 12, GAP = 5;
    private final ModeSetting setting;
    private final IntSupplier customColor;
    private final SmoothValue[] selected;

    public AccentSwatches(ModeSetting setting, IntSupplier customColor) {
        this.setting = setting;
        this.customColor = customColor;
        selected = new SmoothValue[setting.getModes().size()];
        for (int i = 0; i < selected.length; i++) selected[i] = new SmoothValue(setting.index() == i ? 1f : 0f, 16f);
        this.height = 14;
    }

    public static float naturalWidth(int count) {
        return count * (SIZE + GAP) - GAP + 2;
    }

    private int colorOf(String mode) {
        return switch (mode) {
            case "Violet" -> Theme.VIOLET;
            case "Cyan" -> Theme.CYAN;
            case "Emerald" -> Theme.EMERALD;
            case "Rose" -> Theme.ROSE;
            case "Amber" -> Theme.AMBER;
            default -> customColor.getAsInt() | 0xFF000000;
        };
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        List<String> modes = setting.getModes();
        float sx = x + width - naturalWidth(modes.size());
        for (int i = 0; i < modes.size(); i++) {
            float cx = sx + i * (SIZE + GAP) + SIZE / 2f + 1, cy = y + height / 2f;
            float s = selected[i].update(setting.index() == i ? 1f : 0f);
            boolean hov = MathUtil.inside(mx, my, cx - SIZE / 2f, cy - SIZE / 2f, SIZE, SIZE) && UiContext.inClip(mx, my);
            int c = colorOf(modes.get(i));
            Render2D.circle(ctx, cx, cy, SIZE / 2f - 1.5f * s + (hov ? 0.5f : 0f), c);
            if (s > 0.01f) Render2D.ring(ctx, cx, cy, SIZE / 2f + 1f, 1.2f, ColorUtil.mulAlpha(0xFFFFFFFF, s));
            if (modes.get(i).equals("Custom")) IconRender.drawCentered(ctx, Icon.EDIT, cx, cy, 6, ColorUtil.mulAlpha(Theme.onAccentFor(c), 0.9f));
            if (hov) UiContext.tooltip(modes.get(i), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        List<String> modes = setting.getModes();
        float sx = x + width - naturalWidth(modes.size());
        for (int i = 0; i < modes.size(); i++) {
            float cx = sx + i * (SIZE + GAP) + 1;
            if (MathUtil.inside(mx, my, cx, y + height / 2f - SIZE / 2f, SIZE, SIZE) && UiContext.inClip(mx, my)) {
                setting.set(modes.get(i));
                Button.playClick();
                return true;
            }
        }
        return false;
    }
}
