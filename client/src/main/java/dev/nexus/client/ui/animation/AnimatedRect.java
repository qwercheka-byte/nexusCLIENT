package dev.nexus.client.ui.animation;

/**
 * Position and size animation of a rectangle: x, y, width and height tween independently with a shared duration
 * and easing. Use {@link #animateTo} when the target layout changes and read the current values while drawing.
 */
public final class AnimatedRect {
    private final Animation x, y, w, h;

    public AnimatedRect(float x, float y, float w, float h, long durationMs, Easing easing) {
        this.x = new Animation(x, durationMs, easing);
        this.y = new Animation(y, durationMs, easing);
        this.w = new Animation(w, durationMs, easing);
        this.h = new Animation(h, durationMs, easing);
    }

    /** Animates towards a new position and size (components that did not change keep running undisturbed). */
    public AnimatedRect animateTo(float nx, float ny, float nw, float nh) {
        x.animateTo(nx);
        y.animateTo(ny);
        w.animateTo(nw);
        h.animateTo(nh);
        return this;
    }

    /** Animates only the position. */
    public AnimatedRect moveTo(float nx, float ny) {
        x.animateTo(nx);
        y.animateTo(ny);
        return this;
    }

    /** Animates only the size. */
    public AnimatedRect resizeTo(float nw, float nh) {
        w.animateTo(nw);
        h.animateTo(nh);
        return this;
    }

    /** Jumps to the given geometry without animation. */
    public AnimatedRect set(float nx, float ny, float nw, float nh) {
        x.set(nx);
        y.set(ny);
        w.set(nw);
        h.set(nh);
        return this;
    }

    public AnimatedRect duration(long ms) {
        x.duration(ms);
        y.duration(ms);
        w.duration(ms);
        h.duration(ms);
        return this;
    }

    public AnimatedRect easing(Easing easing) {
        x.easing(easing);
        y.easing(easing);
        w.easing(easing);
        h.easing(easing);
        return this;
    }

    public float x() { return x.get(); }
    public float y() { return y.get(); }
    public float width() { return w.get(); }
    public float height() { return h.get(); }

    public boolean isRunning() {
        return x.isRunning() | y.isRunning() | w.isRunning() | h.isRunning();
    }
}
