package dev.nexus.client.ui.vanilla;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

public final class NexusWidgets {
    private static final Map<Object, float[]> STATE = new WeakHashMap<>();
    private static final Map<Object, NexusWidgets.Style> STYLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Object> NO_ICON = Collections.newSetFromMap(new WeakHashMap<>());
    private static final float R = 4.0F;

    private NexusWidgets() {
    }

    public static void setStyle(Object widget, NexusWidgets.Style style) {
        STYLES.put(widget, style);
    }

    public static NexusWidgets.Style style(Object widget) {
        return STYLES.getOrDefault(widget, NexusWidgets.Style.NORMAL);
    }

    static float smooth(Object widget, int slot, float target) {
        float[] s = STATE.computeIfAbsent(widget, kx -> new float[]{0.0F, 0.0F, 0.0F, Float.NaN});
        long now = Util.getMeasuringTimeMs();
        float dt = Float.isNaN(s[3]) ? 1.0F : Math.min(0.1F, (float)(now - (long)s[3]) / 1000.0F);
        s[3] = (float)now;
        float k = 1.0F - (float)Math.exp(-dt * 16.0F);
        s[slot] += (target - s[slot]) * k;
        if (Math.abs(target - s[slot]) < 0.002F) {
            s[slot] = target;
        }

        return s[slot];
    }

    public static void button(DrawContext ctx, Object widget, int x, int y, int w, int h, boolean active, boolean hovered, boolean focused, float alpha) {
        Render2D.pushAlpha(alpha);
        float hov = smooth(widget, 0, !active || !hovered && !focused ? 0.0F : 1.0F);
        NexusWidgets.Style style = style(widget);
        float r = Math.min(h / 2.0F, 12.0F);
        if (!active) {
            Render2D.roundedRect(ctx, x, y, w, h, r, -1609296615);
            Render2D.roundedOutline(ctx, x, y, w, h, r, 1.0F, 352321535);
        } else if (style == NexusWidgets.Style.PRIMARY) {
            if (hov > 0.01F) {
                Render2D.shadow(ctx, x + 2, y + 2, w - 4, h - 2, r, 6.0F * hov, ColorUtil.withAlpha(Theme.accent(), (int)(96.0F * hov)));
            }

            Render2D.roundedRect(ctx, x, y, w, h, r, ColorUtil.lerp(0xB0101216, Theme.accentDim(), 0.20F));
            Render2D.roundedRect(ctx, x, y, w, h, r, ColorUtil.withAlpha(Theme.accent(), (int)(10.0F + 14.0F * hov)));
            Render2D.roundedOutline(ctx, x, y, w, h, r, 1.0F, ColorUtil.withAlpha(Theme.accent(), 200 + (int) (55.0F * hov)));
        } else {
            int bg = ColorUtil.lerp(0xB0101216, 0xD616181D, hov);
            if (style == NexusWidgets.Style.BRAND) {
                bg = ColorUtil.lerp(bg, Theme.accentDim(), 0.18F + 0.14F * hov);
            }

            Render2D.roundedRect(ctx, x, y, w, h, r, bg);
            int border = ColorUtil.lerp(
                style == NexusWidgets.Style.BRAND ? ColorUtil.withAlpha(Theme.accent(), 112) : 520093695, ColorUtil.withAlpha(Theme.accent(), 192), hov
            );
            Render2D.roundedOutline(ctx, x, y, w, h, r, 1.0F, border);
        }

        if (style == NexusWidgets.Style.BRAND && !NO_ICON.contains(widget) && h >= 14) {
            float s = Math.min(14, h - 6);
            float bx = x + 5;
            float by = y + (h - s) / 2.0F;
            Render2D.roundedGradient(ctx, bx, by, s, s, s / 2.0F, Theme.accent(), ColorUtil.lerp(Theme.accent(), -13058568, 0.55F), true);
            IconRender.drawCentered(ctx, Icon.LOGO, bx + s / 2.0F, by + s / 2.0F, s * 0.68F, Theme.onAccent());
        }

        Render2D.popAlpha();
    }


    public static void sliderTrack(
        DrawContext ctx, Object widget, int x, int y, int w, int h, double value, boolean active, boolean hovered, boolean focused, float alpha
    ) {
        Render2D.pushAlpha(alpha);
        float hov = smooth(widget, 0, !active || !hovered && !focused ? 0.0F : 1.0F);
        float r = Math.min(4.0F, h / 2.0F);
        Render2D.roundedRect(ctx, x, y, w, h, r, active ? ColorUtil.lerp(-434628321, -232775383, hov) : -1609296615);
        float fw = (float)((w - 8) * value) + 4.0F;
        if (active && fw > 1.0F) {
            Render2D.roundedRect(
                ctx,
                x,
                y,
                fw + 2.0F,
                h,
                r,
                0.0F,
                0.0F,
                r,
                ColorUtil.withAlpha(Theme.accent(), 48),
                ColorUtil.withAlpha(Theme.accent(), 88 + (int)(32.0F * hov)),
                true,
                true
            );
        }

        Render2D.roundedOutline(ctx, x, y, w, h, r, 1.0F, ColorUtil.lerp(520093695, ColorUtil.withAlpha(Theme.accent(), 160), hov));
        Render2D.popAlpha();
    }

    public static void sliderHandle(DrawContext ctx, Object widget, int x, int y, int w, int h, boolean active, float alpha) {
        Render2D.pushAlpha(alpha);
        float cx = x + w / 2.0F;
        int c = active ? Theme.accentBright() : -10460550;
        Render2D.shadow(ctx, cx - 2.0F, y + 3, 4.0F, h - 6, 2.0F, 3.0F, 1610612736);
        Render2D.roundedRect(ctx, cx - 2.5F, y + 2, 5.0F, h - 4, 2.5F, c);
        Render2D.roundedRect(ctx, cx - 1.5F, y + 3, 3.0F, (h - 6) / 2.0F, 1.5F, 1090519039);
        Render2D.popAlpha();
    }

    public static void textField(DrawContext ctx, Object widget, int x, int y, int w, int h, boolean active, boolean focused) {
        float f = smooth(widget, 0, focused ? 1.0F : 0.0F);
        float r = Math.min(4.0F, h / 2.0F);
        if (f > 0.01F) {
            Render2D.shadow(ctx, x, y, w, h, r, 4.0F * f, ColorUtil.withAlpha(Theme.accent(), (int)(64.0F * f)));
        }

        Render2D.roundedRect(ctx, x, y, w, h, r, active ? -233959660 : -1341124330);
        Render2D.roundedOutline(ctx, x, y, w, h, r, 1.0F, ColorUtil.lerp(654311423, Theme.accent(), f));
    }

    public static void checkbox(DrawContext ctx, Object widget, int x, int y, int size, boolean checked, boolean focused, float alpha) {
        Render2D.pushAlpha(alpha);
        float c = smooth(widget, 0, checked ? 1.0F : 0.0F);
        float f = smooth(widget, 1, focused ? 1.0F : 0.0F);
        float r = Math.min(4.0F, size / 3.0F);
        Render2D.roundedRect(ctx, x, y, size, size, r, ColorUtil.lerp(-233959660, Theme.accent(), c));
        Render2D.roundedOutline(ctx, x, y, size, size, r, 1.0F, ColorUtil.lerp(ColorUtil.lerp(822083583, Theme.accentBright(), f), 1090519039, c));
        if (c > 0.05F) {
            Render2D.pushAlpha(c);
            IconRender.drawCentered(ctx, Icon.CHECK, x + size / 2.0F, y + size / 2.0F, size * 0.7F, Theme.onAccent());
            Render2D.popAlpha();
        }

        Render2D.popAlpha();
    }

    public static void tab(DrawContext ctx, Object widget, int x, int y, int w, int h, boolean current, boolean hovered) {
        float hov = smooth(widget, 0, hovered && !current ? 1.0F : 0.0F);
        float cur = smooth(widget, 1, current ? 1.0F : 0.0F);
        float ix = x + 2;
        float iy = y + 3;
        float iw = w - 4;
        float ih = h - 5;
        if (cur > 0.01F) {
            Render2D.roundedRect(ctx, ix, iy, iw, ih, 4.0F, ColorUtil.withAlpha(Theme.accent(), (int)(44.0F * cur)));
        }

        if (hov > 0.01F) {
            Render2D.roundedRect(ctx, ix, iy, iw, ih, 4.0F, ColorUtil.withAlpha(-1, (int)(16.0F * hov)));
        }

        Render2D.roundedOutline(
            ctx, ix, iy, iw, ih, 4.0F, 1.0F, ColorUtil.lerp(ColorUtil.lerp(352321535, 822083583, hov), ColorUtil.withAlpha(Theme.accent(), 144), cur)
        );
    }

    public static void tabUnderline(DrawContext ctx, int x, int y, int w) {
        Render2D.roundedRect(ctx, x, y - 0.5F, w, 1.5F, 0.75F, Theme.accentBright());
    }

    public static void listBackground(DrawContext ctx, int x, int y, int w, int h, boolean inWorld) {
        Render2D.rect(ctx, x, y, w, h, inWorld ? 1342177280 : 1610941960);
    }

    public static void listSeparators(DrawContext ctx, int x, int top, int bottom, int w) {
        separator(ctx, x, top - 1, w);
        separator(ctx, x, bottom, w);
    }

    private static void separator(DrawContext ctx, float x, float y, float w) {
        Render2D.rect(ctx, x, y, w, 1.0F, 452984831);
        float lw = w * 0.35F;
        float cx = x + w / 2.0F;
        Render2D.gradientH(ctx, cx - lw, y, lw, 1.0F, ColorUtil.withAlpha(Theme.accent(), 0), ColorUtil.withAlpha(Theme.accent(), 176));
        Render2D.gradientH(ctx, cx, y, lw, 1.0F, ColorUtil.withAlpha(Theme.accent(), 176), ColorUtil.withAlpha(Theme.accent(), 0));
    }

    public static void selection(DrawContext ctx, int x, int y, int w, int h, boolean focused) {
        Render2D.roundedRect(ctx, x, y, w, h, 4.0F, Theme.accentFaint());
        Render2D.roundedOutline(ctx, x, y, w, h, 4.0F, 1.0F, focused ? Theme.accent() : ColorUtil.withAlpha(Theme.accent(), 144));
    }

    public static void scrollbar(DrawContext ctx, int x, int y, int w, int h, boolean thumb) {
        float cx = x + w / 2.0F;
        if (thumb) {
            Render2D.roundedRect(ctx, cx - 1.5F, y + 1, 3.0F, h - 2, 1.5F, ColorUtil.withAlpha(Theme.accentBright(), 208));
        } else {
            Render2D.roundedRect(ctx, cx - 1.5F, y, 3.0F, h, 1.5F, 587202559);
        }
    }

    public static void darkening(DrawContext ctx, int x, int y, int w, int h, boolean inWorld) {
        if (inWorld) {
            Render2D.gradientV(ctx, x, y, w, h, -1878390001, -1341453296);
        } else {
            Render2D.gradientV(ctx, x, y, w, h, -1207367154, -670364655);
        }

        Render2D.gradientV(ctx, x, y, w, Math.min(h, 60), ColorUtil.withAlpha(Theme.accent(), 24), 0);
    }

    public static void inGameBackground(DrawContext ctx, int w, int h) {
        Render2D.gradientV(ctx, 0.0F, 0.0F, w, h, -1609954545, -1073017840);
    }

    public static enum Style {
        NORMAL,
        PRIMARY,
        BRAND;
    }
}
