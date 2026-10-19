package dev.nexus.client.ui.component;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.MultiSelectSetting;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Wrapping chips, each toggling one option of a {@link MultiSelectSetting}. */
public class MultiSelect extends Component {
    private static final float CHIP_H = 15, GAP = 4;
    private final MultiSelectSetting setting;
    private final SmoothValue[] selected;
    private final float[] chipX, chipY, chipW;

    public MultiSelect(MultiSelectSetting setting) {
        this.setting = setting;
        int n = setting.getOptions().size();
        selected = new SmoothValue[n];
        chipX = new float[n];
        chipY = new float[n];
        chipW = new float[n];
        for (int i = 0; i < n; i++) {
            selected[i] = new SmoothValue(setting.isSelected(setting.getOptions().get(i)) ? 1f : 0f, 18f);
        }
    }

    private float layout(float originX, float originY, float maxW) {
        List<String> opts = setting.getOptions();
        float cx = 0, cy = 0;
        for (int i = 0; i < opts.size(); i++) {
            float w = TextRender.width(opts.get(i)) + 22;
            if (cx > 0 && cx + w > maxW) {
                cx = 0;
                cy += CHIP_H + GAP;
            }
            chipX[i] = originX + cx;
            chipY[i] = originY + cy;
            chipW[i] = w;
            cx += w + GAP;
        }
        return cy + CHIP_H;
    }

    @Override
    public float preferredHeight(float width) {
        return layout(0, 0, width);
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        layout(x, y, width);
        List<String> opts = setting.getOptions();
        for (int i = 0; i < opts.size(); i++) {
            float s = selected[i].update(setting.isSelected(opts.get(i)) ? 1f : 0f);
            boolean hov = MathUtil.inside(mx, my, chipX[i], chipY[i], chipW[i], CHIP_H) && UiContext.inClip(mx, my);
            int bg = ColorUtil.lerp(hov ? Theme.CONTROL_HOVER : Theme.CONTROL, Theme.accentSoft(), s);
            Render2D.roundedRect(ctx, chipX[i], chipY[i], chipW[i], CHIP_H, CHIP_H / 2f, bg);
            Render2D.roundedOutline(ctx, chipX[i], chipY[i], chipW[i], CHIP_H, CHIP_H / 2f, 1f, ColorUtil.lerp(Theme.BORDER, Theme.accent(), s));
            Render2D.pushAlpha(s);
            IconRender.draw(ctx, Icon.CHECK, chipX[i] + 5, chipY[i] + 3.5f, 8, Theme.accentBright());
            Render2D.popAlpha();
            TextRender.draw(ctx, opts.get(i), chipX[i] + 15, TextRender.centerY(chipY[i], CHIP_H), ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, s));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !UiContext.inClip(mx, my)) return false;
        List<String> opts = setting.getOptions();
        for (int i = 0; i < opts.size(); i++) {
            if (MathUtil.inside(mx, my, chipX[i], chipY[i], chipW[i], CHIP_H)) {
                setting.toggle(opts.get(i));
                Button.playClick();
                return true;
            }
        }
        return false;
    }
}
