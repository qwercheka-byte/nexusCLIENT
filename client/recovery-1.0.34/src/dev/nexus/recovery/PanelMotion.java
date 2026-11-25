package dev.nexus.recovery;

import dev.nexus.client.ui.animation.AnimationSettings;
import java.util.WeakHashMap;

/** Cached once per render; click and scroll layout use the last displayed position. */
public final class PanelMotion {
    private static final WeakHashMap<Object, State> STATES = new WeakHashMap<>();
    private static final class State { long last; float remaining = 1; }
    public static void frame(Object panel) {
        long now = System.nanoTime();
        State s = STATES.get(panel);
        if (s == null) { s = new State(); s.last = now; STATES.put(panel, s); }
        double dt = Math.max(0, Math.min(0.1, (now - s.last) / 1_000_000_000.0));
        s.last = now;
        s.remaining = AnimationSettings.instant() ? 0 : (float)(s.remaining * Math.exp(-14 * AnimationSettings.speed() * dt));
        if (s.remaining < 0.002f) s.remaining = 0;
    }
    public static int offset(Object panel, float unit) {
        State s = STATES.get(panel);
        return s == null ? 0 : Math.round(20 * unit * s.remaining);
    }
}
