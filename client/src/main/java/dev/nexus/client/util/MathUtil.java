package dev.nexus.client.util;

public final class MathUtil {
    private MathUtil() {}

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static double roundToStep(double value, double step) {
        if (step <= 0) return value;
        return Math.round(value / step) * step;
    }

    /** Number of decimals required to display a step value (0.05 -> 2). */
    public static int decimalsFor(double step) {
        if (step <= 0) return 2;
        int decimals = 0;
        double s = step;
        while (decimals < 6 && Math.abs(s - Math.rint(s)) > 1e-9) {
            s *= 10;
            decimals++;
        }
        return decimals;
    }

    public static boolean inside(double px, double py, float x, float y, float w, float h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}
