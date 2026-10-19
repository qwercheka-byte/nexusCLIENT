package dev.nexus.client.ui.animation;

/**
 * Opacity animation (0 = invisible, 1 = fully visible) with separate fade-in and fade-out durations.
 * Typical use: screens and pop-ups fade in when opened and fade out before they are removed.
 */
public final class Fade {
    private final Animation alpha;
    private long inMs;
    private long outMs;

    public Fade(long inMs, long outMs, Easing easing) {
        this.alpha = new Animation(0f, inMs, easing);
        this.inMs = inMs;
        this.outMs = outMs;
    }

    /** Starts fading in from the current opacity. */
    public Fade fadeIn() {
        alpha.animateTo(1f, inMs);
        return this;
    }

    /** Restarts a fade-in from fully transparent. */
    public Fade restartIn() {
        alpha.duration(inMs).restart(0f, 1f);
        return this;
    }

    public Fade fadeOut() {
        alpha.animateTo(0f, outMs);
        return this;
    }

    /** Fades in or out depending on {@code visible}; calling it every frame with the same value is free. */
    public Fade show(boolean visible) {
        return visible ? fadeIn() : fadeOut();
    }

    public Fade set(float opacity) {
        alpha.set(opacity);
        return this;
    }

    public Fade durations(long inMs, long outMs) {
        this.inMs = inMs;
        this.outMs = outMs;
        return this;
    }

    public Fade easing(Easing easing) {
        alpha.easing(easing);
        return this;
    }

    /** Current opacity, clamped to 0..1. */
    public float alpha() {
        float a = alpha.get();
        return a < 0f ? 0f : Math.min(a, 1f);
    }

    /** True while anything of the element can be seen (visible or still fading out). */
    public boolean isVisible() {
        return alpha() > 0.002f || alpha.target() > 0f;
    }

    /** True when a fade-out has completely finished. */
    public boolean isFadedOut() {
        return alpha.target() <= 0f && alpha.isFinished();
    }

    public boolean isRunning() {
        return alpha.isRunning();
    }
}
