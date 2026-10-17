package dev.nexus.client.ui.animation;

/**
 * Animation of a two-state control (switch, checkbox, enabled indicator). Feed it the current state every frame
 * with {@link #update(boolean)}; it starts a new tween only when the state actually changes.
 */
public final class ToggleAnimation {
    private final Animation anim;

    public ToggleAnimation(boolean initial, long durationMs, Easing easing) {
        this.anim = new Animation(initial ? 1f : 0f, durationMs, easing);
    }

    /** Default switch feel: 220 ms with a small overshoot. */
    public static ToggleAnimation forSwitch(boolean initial) {
        return new ToggleAnimation(initial, 220, Easing.OUT_BACK);
    }

    /** Updates the state and returns the eased value (may overshoot slightly with {@link Easing#OUT_BACK}). */
    public float update(boolean on) {
        anim.animateTo(on ? 1f : 0f);
        return anim.get();
    }

    /** Current value (may overshoot slightly). */
    public float value() {
        return anim.get();
    }

    /** Current value clamped to 0..1, for colours and opacity. */
    public float clamped() {
        float v = anim.get();
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    public ToggleAnimation snap(boolean on) {
        anim.set(on ? 1f : 0f);
        return this;
    }

    public ToggleAnimation duration(long ms) {
        anim.duration(ms);
        return this;
    }

    public ToggleAnimation easing(Easing easing) {
        anim.easing(easing);
        return this;
    }
}
