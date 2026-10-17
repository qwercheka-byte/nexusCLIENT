package dev.nexus.client.ui.animation;

/**
 * Critically damped "follow" value: approaches its target exponentially using the real elapsed time
 * ({@link AnimationClock}), so the result does not depend on the frame rate.
 * Ideal for hover highlights, scrolling and positions that change often. No allocations.
 */
public final class SmoothValue {
    private float value;
    private float target;
    private float speed;
    private long lastNanos = Long.MIN_VALUE;

    /** @param speed higher = snappier (≈ 1/time-constant in seconds, 12-20 is a good UI range) */
    public SmoothValue(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public SmoothValue setTarget(float target) {
        this.target = target;
        return this;
    }

    public SmoothValue speed(float speed) {
        this.speed = speed;
        return this;
    }

    public SmoothValue snap(float v) {
        this.value = v;
        this.target = v;
        return this;
    }

    /** Advances using the elapsed time since the previous call and returns the new value. */
    public float update() {
        long now = AnimationClock.now();
        if (lastNanos == Long.MIN_VALUE) lastNanos = now;
        // clamp: a long pause (screen closed) must not teleport, a negative step must not reverse
        float dt = Math.max(0f, Math.min(0.1f, (now - lastNanos) / 1_000_000_000f));
        lastNanos = now;
        if (AnimationSettings.instant()) {
            value = target;
            return value;
        }
        float k = 1f - (float) Math.exp(-speed * AnimationSettings.speed() * dt);
        value += (target - value) * k;
        if (Math.abs(target - value) < 0.0005f) value = target;
        return value;
    }

    public float update(float newTarget) {
        target = newTarget;
        return update();
    }

    public float get() {
        return value;
    }

    public float getTarget() {
        return target;
    }
}
