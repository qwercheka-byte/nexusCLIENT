package dev.nexus.client.ui.component;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.Scissor;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.ColorSetting;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * Expandable HSV colour picker: saturation/value square, hue bar, optional alpha bar and presets.
 * The collapsed header shows a swatch and the hex value on the right side of the row.
 */
public class ColorPicker extends Component {
    private static final float HEADER = 16, SV_HEIGHT = 64, BAR = 7, SPACING = 6, PRESET = 11;
    private static final int[] PRESETS = {0xFFFFFFFF, 0xFF000000, Theme.VIOLET, Theme.CYAN, Theme.EMERALD,
            Theme.ROSE, Theme.AMBER, 0xFFFF5A5A, 0xFF5DF08A, 0xFF6C8CFF};

    private final ColorSetting setting;
    private final SmoothValue expand = new SmoothValue(0f, 16f);
    private final float[] hsv = new float[3];
    private int syncedColor;
    private boolean open;
    private int drag; // 0 none, 1 sv, 2 hue, 3 alpha

    public ColorPicker(ColorSetting setting) {
        this.setting = setting;
        sync();
    }

    private void sync() {
        syncedColor = setting.get();
        ColorUtil.rgbToHsv(syncedColor, hsv);
    }

    private float expandedHeight() {
        return SPACING + SV_HEIGHT + SPACING + BAR + (setting.allowsAlpha() ? SPACING + BAR : 0) + SPACING + PRESET + 2;
    }

    @Override
    public float preferredHeight(float width) {
        return HEADER + expandedHeight() * expand.get();
    }

    private float svY() { return y + HEADER + SPACING; }
    private float hueY() { return svY() + SV_HEIGHT + SPACING; }
    private float alphaY() { return hueY() + BAR + SPACING; }
    private float presetY() { return (setting.allowsAlpha() ? alphaY() : hueY()) + BAR + SPACING; }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        if (drag == 0 && setting.get() != syncedColor) {
            float keepHue = hsv[0];
            sync();
            if (hsv[1] == 0 || hsv[2] == 0) hsv[0] = keepHue; // grey loses hue information
        }
        float e = expand.update(open ? 1f : 0f);
        float hv = hover.update(isHeaderHovered(mx, my) ? 1f : 0f);
        int color = setting.get();

        // header swatch (right aligned)
        String hex = ColorUtil.toHex(color, setting.allowsAlpha());
        float sw = 26, sx = x + width - sw;
        float hexW = TextRender.width(hex);
        TextRender.draw(ctx, hex, sx - 6 - hexW, TextRender.centerY(y, HEADER), ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv));
        checker(ctx, sx, y + 2, sw, HEADER - 4, 3);
        Render2D.roundedRect(ctx, sx, y + 2, sw, HEADER - 4, 3, color);
        Render2D.roundedOutline(ctx, sx, y + 2, sw, HEADER - 4, 3, 1f, open ? Theme.accent() : ColorUtil.lerp(Theme.BORDER, Theme.BORDER_STRONG, hv));

        if (e < 0.01f) return;
        float bodyH = expandedHeight() * e;
        Scissor.push(ctx, x - 2, y + HEADER, width + 4, bodyH);
        UiContext.pushClip(x - 2, y + HEADER, width + 4, bodyH);
        Render2D.pushAlpha(e);

        // saturation / value square
        float svX = x, svW = width;
        int hueColor = ColorUtil.hsvToRgb(hsv[0], 1, 1, 255);
        Render2D.roundedGradient(ctx, svX, svY(), svW, SV_HEIGHT, 3, 0xFFFFFFFF, hueColor, true);
        Render2D.roundedGradient(ctx, svX, svY(), svW, SV_HEIGHT, 3, 0x00000000, 0xFF000000, false);
        float px = svX + hsv[1] * svW, py = svY() + (1 - hsv[2]) * SV_HEIGHT;
        Render2D.ring(ctx, px, py, 3.5f, 1.2f, 0xFFFFFFFF);
        Render2D.ring(ctx, px, py, 4.6f, 1f, 0x60000000);

        // hue bar
        float hy = hueY();
        float seg = width / 6f;
        for (int i = 0; i < 6; i++) {
            int c1 = ColorUtil.hsvToRgb(i / 6f, 1, 1, 255), c2 = ColorUtil.hsvToRgb((i + 1) / 6f, 1, 1, 255);
            Render2D.gradientH(ctx, x + seg * i, hy, seg + (i < 5 ? 0.5f : 0), BAR, c1, c2);
        }
        knob(ctx, x + hsv[0] * width, hy, hueColor);

        // alpha bar
        if (setting.allowsAlpha()) {
            float ay = alphaY();
            checker(ctx, x, ay, width, BAR, 0);
            Render2D.gradientH(ctx, x, ay, width, BAR, color & 0x00FFFFFF, color | 0xFF000000);
            knob(ctx, x + ColorUtil.alpha(color) / 255f * width, ay, color | 0xFF000000);
        }

        // presets
        float pyy = presetY();
        for (int i = 0; i < PRESETS.length; i++) {
            float cx = x + i * (PRESET + 4);
            if (cx + PRESET > x + width) break;
            boolean hovered = MathUtil.inside(mx, my, cx, pyy, PRESET, PRESET) && UiContext.inClip(mx, my);
            Render2D.roundedRect(ctx, cx, pyy, PRESET, PRESET, 3, PRESETS[i]);
            Render2D.roundedOutline(ctx, cx, pyy, PRESET, PRESET, 3, 1f, hovered ? 0xFFFFFFFF : Theme.BORDER_STRONG);
        }
        Render2D.popAlpha();
        UiContext.popClip();
        Scissor.pop(ctx);
    }

    private void knob(DrawContext ctx, float kx, float ky, int fill) {
        Render2D.roundedRect(ctx, kx - 2.5f, ky - 1.5f, 5, BAR + 3, 2, 0xFFFFFFFF);
        Render2D.roundedRect(ctx, kx - 1.5f, ky - 0.5f, 3, BAR + 1, 1.5f, fill);
    }

    private static void checker(DrawContext ctx, float x, float y, float w, float h, float r) {
        Render2D.roundedRect(ctx, x, y, w, h, r, 0xFFBFBFBF);
        float s = 3.5f;
        for (float yy = 0; yy < h; yy += s) {
            for (float xx = ((int) (yy / s) % 2) * s; xx < w; xx += s * 2) {
                Render2D.rect(ctx, x + xx, y + yy, Math.min(s, w - xx), Math.min(s, h - yy), 0xFF8A8A8A);
            }
        }
    }

    private boolean isHeaderHovered(double mx, double my) {
        return MathUtil.inside(mx, my, x + width - 140, y, 140, HEADER) && UiContext.inClip(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (isHeaderHovered(mx, my) && button == 0) {
            open = !open;
            Button.playClick();
            return true;
        }
        if (!open || expand.get() < 0.9f || button != 0 || !UiContext.inClip(mx, my)) return false;
        if (MathUtil.inside(mx, my, x, svY(), width, SV_HEIGHT)) drag = 1;
        else if (MathUtil.inside(mx, my, x - 2, hueY() - 2, width + 4, BAR + 4)) drag = 2;
        else if (setting.allowsAlpha() && MathUtil.inside(mx, my, x - 2, alphaY() - 2, width + 4, BAR + 4)) drag = 3;
        else {
            float pyy = presetY();
            for (int i = 0; i < PRESETS.length; i++) {
                float cx = x + i * (PRESET + 4);
                if (MathUtil.inside(mx, my, cx, pyy, PRESET, PRESET)) {
                    int a = setting.allowsAlpha() ? ColorUtil.alpha(setting.get()) : 255;
                    setting.set(ColorUtil.withAlpha(PRESETS[i], a));
                    return true;
                }
            }
            return false;
        }
        UiContext.startDrag(this);
        mouseDragged(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (drag == 0) return false;
        int alpha = ColorUtil.alpha(setting.get());
        switch (drag) {
            case 1 -> {
                hsv[1] = MathUtil.clamp((float) ((mx - x) / width), 0, 1);
                hsv[2] = 1 - MathUtil.clamp((float) ((my - svY()) / SV_HEIGHT), 0, 1);
            }
            case 2 -> hsv[0] = MathUtil.clamp((float) ((mx - x) / width), 0, 0.999f);
            case 3 -> alpha = Math.round(MathUtil.clamp((float) ((mx - x) / width), 0, 1) * 255);
            default -> { }
        }
        int c = ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2], setting.allowsAlpha() ? alpha : 255);
        setting.set(c);
        syncedColor = setting.get();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag == 0) return false;
        drag = 0;
        UiContext.stopDrag();
        return true;
    }
}
