package dev.nexus.client.ui.animation;

/**
 * Transition between two pages: the outgoing page fades out while sliding slightly away, the incoming page fades in
 * while sliding into place. The slide direction follows the navigation direction (next page from below,
 * previous page from above). Works with page indices, so it does not allocate.
 */
public final class PageTransition {
    private final Animation progress;
    private final float distance;
    private int current;
    private int previous = -1;
    private float direction = 1f;

    /**
     * @param initialPage page shown at start (no transition)
     * @param distance    slide distance in pixels
     */
    public PageTransition(int initialPage, long durationMs, Easing easing, float distance) {
        this.progress = new Animation(1f, durationMs, easing);
        this.current = initialPage;
        this.distance = distance;
    }

    /** Switches to {@code page}; returns false if it is already the current page. */
    public boolean show(int page) {
        if (page == current) return false;
        direction = page > current ? 1f : -1f;
        previous = current;
        current = page;
        progress.restart(0f, 1f);
        return true;
    }

    public PageTransition duration(long ms) {
        progress.duration(ms);
        return this;
    }

    public int current() {
        return current;
    }

    /** The page being animated out, or -1 when no transition is running. */
    public int previous() {
        if (previous >= 0 && progress.isFinished()) previous = -1;
        return previous;
    }

    /** Eased progress 0..1 of the running transition (1 when idle). */
    public float progress() {
        return progress.get();
    }

    public float incomingAlpha() {
        return clamp01(progress.get());
    }

    public float outgoingAlpha() {
        return 1f - clamp01(progress.get());
    }

    /** Vertical offset of the incoming page in pixels (approaches 0). */
    public float incomingOffset() {
        return direction * distance * (1f - progress.get());
    }

    /** Vertical offset of the outgoing page in pixels (moves away from 0). */
    public float outgoingOffset() {
        return -direction * distance * 0.8f * progress.get();
    }

    public boolean isRunning() {
        return progress.isRunning();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
