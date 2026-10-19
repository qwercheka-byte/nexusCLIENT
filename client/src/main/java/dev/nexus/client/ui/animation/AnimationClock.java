package dev.nexus.client.ui.animation;

/**
 * Single time source of the animation system.
 * <p>
 * Every animation reads the time from here instead of calling {@link System#nanoTime()} itself. Screens and the HUD
 * call {@link #beginFrame()} once at the start of a frame, so all animations drawn in that frame see exactly the same
 * timestamp (no drift between elements). If no frame has started recently – e.g. a value is read from a tick
 * handler – the live clock is used, so animations never freeze. Primitive fields only: no allocations.
 */
public final class AnimationClock {
    /** A captured frame time older than this is considered stale (a frame at 20 FPS takes 50 ms). */
    private static final long FRAME_VALIDITY_NANOS = 60_000_000L;
    private static long frameNanos = Long.MIN_VALUE;

    private AnimationClock() {}

    /** Captures the timestamp of the frame that is about to be drawn. Cheap; safe to call several times per frame. */
    public static void beginFrame() {
        long now = System.nanoTime();
        // several renderers call this per frame (HUD, screen): keep the first timestamp of the frame
        if (frameNanos == Long.MIN_VALUE || now - frameNanos > 4_000_000L) frameNanos = now;
    }

    /** Current animation time in nanoseconds (monotonic, arbitrary origin). */
    public static long now() {
        long live = System.nanoTime();
        if (frameNanos != Long.MIN_VALUE && live - frameNanos < FRAME_VALIDITY_NANOS) return frameNanos;
        return live;
    }

    /** Animation time in milliseconds. */
    public static long millis() {
        return now() / 1_000_000L;
    }

    /** Smooth periodic wave 0..1..0 with the given period (pulsing highlights). */
    public static float wave(long periodMs) {
        double phase = (millis() % periodMs) / (double) periodMs;
        return (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
    }

    /** On/off square wave (caret blinking): true during the first half of every period. */
    public static boolean blink(long periodMs) {
        return millis() % periodMs < periodMs / 2;
    }

    /**
     * Opacity of something that stays fully visible for {@code holdMs} after {@code startMs} ({@link #millis()} time
     * base) and then fades out linearly over {@code fadeMs}. Used for hints that disappear by themselves.
     */
    public static float holdThenFade(long startMs, long holdMs, long fadeMs) {
        long t = millis() - startMs - holdMs;
        if (t <= 0) return 1f;
        if (t >= fadeMs || AnimationSettings.instant()) return 0f;
        return 1f - t / (float) fadeMs;
    }
}
