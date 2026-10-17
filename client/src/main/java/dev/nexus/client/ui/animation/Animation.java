package dev.nexus.client.ui.animation;

/**
 * Time based tween between two values with a configurable duration and {@link Easing} curve.
 * <p>
 * Progress is computed from the elapsed time ({@link AnimationClock}), never from the number of rendered frames, so
 * the speed is the same at 30 and at 300 FPS. The global speed multiplier of {@link AnimationSettings} is applied
 * here, in one place. No allocations after construction: all state is primitive.
 */
public final class Animation {
    private float from;
    private float to;
    private long startNanos;
    private long durationNanos;
    private Easing easing;
    private boolean running;

    public Animation(float initial, long durationMs, Easing easing) {
        this.from = initial;
        this.to = initial;
        this.durationNanos = Math.max(0L, durationMs) * 1_000_000L;
        this.easing = easing;
    }

    /** Starts animating from the current value to {@code target} (no-op if already heading there). */
    public Animation animateTo(float target) {
        if (target == to) return this;
        from = get();
        to = target;
        startNanos = AnimationClock.now();
        running = true;
        return this;
    }

    /** Like {@link #animateTo(float)} with a new duration for this and the following runs. */
    public Animation animateTo(float target, long durationMs) {
        duration(durationMs);
        return animateTo(target);
    }

    /** Jumps to a value immediately. */
    public Animation set(float value) {
        from = value;
        to = value;
        running = false;
        return this;
    }

    /** Restarts from {@code start} to {@code target}. */
    public Animation restart(float start, float target) {
        from = start;
        to = target;
        startNanos = AnimationClock.now();
        running = true;
        return this;
    }

    public Animation duration(long durationMs) {
        this.durationNanos = Math.max(0L, durationMs) * 1_000_000L;
        return this;
    }

    public Animation easing(Easing easing) {
        this.easing = easing;
        return this;
    }

    public long durationMs() {
        return durationNanos / 1_000_000L;
    }

    public Easing easing() {
        return easing;
    }

    /** Current (eased) value. */
    public float get() {
        if (!running) return to;
        float t = rawProgress();
        if (t >= 1f || AnimationSettings.instant()) {
            running = false;
            return to;
        }
        return from + (to - from) * easing.apply(t);
    }

    /** Linear (un-eased) progress 0..1 of the current run. */
    public float progress() {
        return running ? Math.min(1f, rawProgress()) : 1f;
    }

    private float rawProgress() {
        float scaled = durationNanos / AnimationSettings.speed();
        if (scaled <= 0f) return 1f;
        return Math.max(0f, (AnimationClock.now() - startNanos) / scaled);
    }

    public float target() {
        return to;
    }

    public boolean isRunning() {
        if (running) get(); // refresh state
        return running;
    }

    public boolean isFinished() {
        return !isRunning();
    }
}
