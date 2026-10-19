package dev.nexus.client.util;

import dev.nexus.client.util.Lang;
public final class TimeFormat {
    private TimeFormat() {}

    private static final String[] CACHE = new String[6000];

    /** Formats ticks (20/s) as m:ss or h:mm:ss. Values below 100 minutes are cached (no allocation). */
    public static String ticks(int ticks) {
        return cachedSeconds(Math.max(0, ticks) / 20);
    }

    /** Cached variant of {@link #seconds(long)} for render loops (main thread). */
    public static String cachedSeconds(int totalSeconds) {
        if (totalSeconds < 0 || totalSeconds >= CACHE.length) return seconds(totalSeconds);
        String s = CACHE[totalSeconds];
        if (s == null) CACHE[totalSeconds] = s = seconds(totalSeconds);
        return s;
    }

    public static String seconds(long totalSeconds) {
        long h = totalSeconds / 3600, m = (totalSeconds % 3600) / 60, s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder(8);
        if (h > 0) {
            sb.append(h).append(':');
            if (m < 10) sb.append('0');
        }
        sb.append(m).append(':');
        if (s < 10) sb.append('0');
        sb.append(s);
        return sb.toString();
    }

    /** "1h 04m", "12m 05s", "42s". */
    public static String humanDuration(long totalSeconds) {
        long h = totalSeconds / 3600, m = (totalSeconds % 3600) / 60, s = totalSeconds % 60;
        if (h > 0) return Lang.fmt("{0}h {1}m", h, (m < 10 ? "0" : "") + m);
        if (m > 0) return Lang.fmt("{0}m {1}s", m, (s < 10 ? "0" : "") + s);
        return Lang.fmt("{0}s", s);
    }
}
