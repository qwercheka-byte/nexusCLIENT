package dev.nexus.client.util;

import java.util.Locale;

/** ARGB colour helpers. All colours are packed 0xAARRGGBB ints. */
public final class ColorUtil {
    private ColorUtil() {}

    public static int alpha(int c) { return (c >>> 24) & 0xFF; }
    public static int red(int c)   { return (c >> 16) & 0xFF; }
    public static int green(int c) { return (c >> 8) & 0xFF; }
    public static int blue(int c)  { return c & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (MathUtil.clamp(a, 0, 255) << 24) | (MathUtil.clamp(r, 0, 255) << 16)
                | (MathUtil.clamp(g, 0, 255) << 8) | MathUtil.clamp(b, 0, 255);
    }

    /** Replaces the alpha channel. */
    public static int withAlpha(int c, int a) {
        return (MathUtil.clamp(a, 0, 255) << 24) | (c & 0x00FFFFFF);
    }

    /** Multiplies the existing alpha channel by {@code factor} (0..1). */
    public static int mulAlpha(int c, float factor) {
        if (factor >= 1f) return c;
        int a = Math.round(alpha(c) * MathUtil.clamp(factor, 0f, 1f));
        return (a << 24) | (c & 0x00FFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = MathUtil.clamp(t, 0f, 1f);
        return argb(
                Math.round(alpha(a) + (alpha(b) - alpha(a)) * t),
                Math.round(red(a) + (red(b) - red(a)) * t),
                Math.round(green(a) + (green(b) - green(a)) * t),
                Math.round(blue(a) + (blue(b) - blue(a)) * t));
    }

    /** Brightens (factor &gt; 1) or darkens (factor &lt; 1) the rgb channels. */
    public static int shade(int c, float factor) {
        return argb(alpha(c), Math.round(red(c) * factor), Math.round(green(c) * factor), Math.round(blue(c) * factor));
    }

    public static int hsvToRgb(float h, float s, float v, int alpha) {
        h = ((h % 1f) + 1f) % 1f;
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return argb(alpha, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    /** Writes h, s, v (each 0..1) into {@code out}. */
    public static void rgbToHsv(int c, float[] out) {
        float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }

    public static String toHex(int c, boolean withAlpha) {
        return withAlpha ? String.format(Locale.ROOT, "#%08X", c) : String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
    }

    /**
     * Parses #RGB, #RRGGBB or #AARRGGBB (the leading # is optional).
     * @return the colour or {@code null} if the string is not a valid colour
     */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.startsWith("#")) v = v.substring(1);
        try {
            if (v.length() == 3) {
                int r = Integer.parseInt(v.substring(0, 1), 16), g = Integer.parseInt(v.substring(1, 2), 16),
                        b = Integer.parseInt(v.substring(2, 3), 16);
                return argb(255, r * 17, g * 17, b * 17);
            }
            if (v.length() == 6) return 0xFF000000 | Integer.parseInt(v, 16);
            if (v.length() == 8) return (int) Long.parseLong(v, 16);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }
}
