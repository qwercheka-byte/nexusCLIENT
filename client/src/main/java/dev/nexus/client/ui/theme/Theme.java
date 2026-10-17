package dev.nexus.client.ui.theme;

import dev.nexus.client.util.ColorUtil;

/**
 * NEXUS visual language: graphite surfaces, one accent colour, consistent spacing and radii.
 * Accent-derived colours are recomputed whenever the accent changes, never per frame.
 */
public final class Theme {
    // surfaces
    public static final int BACKDROP = 0xF00E0F13;
    public static final int SIDEBAR = 0xF4121318;
    public static final int SURFACE = 0xFF16171D;
    public static final int CARD = 0xFF1B1C23;
    public static final int CARD_HOVER = 0xFF22232C;
    public static final int CONTROL = 0xFF262732;
    public static final int CONTROL_HOVER = 0xFF2E2F3B;
    public static final int BORDER = 0x1AFFFFFF;
    public static final int BORDER_STRONG = 0x2EFFFFFF;
    public static final int DIVIDER = 0x12FFFFFF;
    public static final int SHADOW = 0x66000000;
    // text
    public static final int TEXT = 0xFFECEDF3;
    public static final int TEXT_MUTED = 0xFF9A9CAD;
    public static final int TEXT_DIM = 0xFF60627A;
    // states
    public static final int SUCCESS = 0xFF34D399;
    public static final int WARNING = 0xFFFBBF24;
    public static final int DANGER = 0xFFF87171;
    // HUD
    public static final int HUD_PANEL = 0xFF101116;

    // spacing / shape
    public static final float RADIUS = 6f;
    public static final float RADIUS_SMALL = 4f;
    public static final float PAD = 10f;
    public static final float GAP = 6f;
    public static final float ROW_HEIGHT = 24f;

    public static final int VIOLET = 0xFF8B5CF6;
    public static final int CYAN = 0xFF38BDF8;
    public static final int EMERALD = 0xFF10B981;
    public static final int ROSE = 0xFFF43F5E;
    public static final int AMBER = 0xFFF59E0B;

    private static int accent = VIOLET;
    private static int accentDim, accentBright, accentSoft, accentFaint, onAccent;

    static {
        setAccent(VIOLET);
    }

    private Theme() {}

    public static void setAccent(int color) {
        accent = color | 0xFF000000;
        accentDim = ColorUtil.shade(accent, 0.62f);
        accentBright = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.25f);
        accentSoft = ColorUtil.withAlpha(accent, 0x40);
        accentFaint = ColorUtil.withAlpha(accent, 0x1C);
        int luminance = (ColorUtil.red(accent) * 299 + ColorUtil.green(accent) * 587 + ColorUtil.blue(accent) * 114) / 1000;
        onAccent = luminance > 170 ? 0xFF111217 : 0xFFFFFFFF;
    }

    public static int accent() { return accent; }
    public static int accentDim() { return accentDim; }
    public static int accentBright() { return accentBright; }
    /** Accent at ~25% alpha (selection backgrounds). */
    public static int accentSoft() { return accentSoft; }
    /** Accent at ~11% alpha (subtle tints). */
    public static int accentFaint() { return accentFaint; }
    /** Readable text colour on top of the accent. */
    public static int onAccent() { return onAccent; }

    /** Readable foreground (dark or white) for an arbitrary background colour. */
    public static int onAccentFor(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 170 ? 0xFF111217 : 0xFFFFFFFF;
    }
}
