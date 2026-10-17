package dev.nexus.client.ui.animation;

/** Easing curves mapping linear progress t (0..1) to eased progress. Pure functions, no allocations. */
public enum Easing {
    LINEAR {
        @Override public float apply(float t) { return t; }
    },
    IN_QUAD {
        @Override public float apply(float t) { return t * t; }
    },
    OUT_QUAD {
        @Override public float apply(float t) { return 1 - (1 - t) * (1 - t); }
    },
    IN_OUT_QUAD {
        @Override public float apply(float t) { return t < 0.5f ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2; }
    },
    IN_CUBIC {
        @Override public float apply(float t) { return t * t * t; }
    },
    OUT_CUBIC {
        @Override public float apply(float t) { float f = 1 - t; return 1 - f * f * f; }
    },
    IN_OUT_CUBIC {
        @Override public float apply(float t) {
            if (t < 0.5f) return 4 * t * t * t;
            float f = -2 * t + 2;
            return 1 - f * f * f / 2;
        }
    },
    OUT_QUART {
        @Override public float apply(float t) { float f = 1 - t; return 1 - f * f * f * f; }
    },
    IN_OUT_SINE {
        @Override public float apply(float t) { return (float) (-(Math.cos(Math.PI * t) - 1) / 2); }
    },
    OUT_EXPO {
        @Override public float apply(float t) { return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t); }
    },
    /** Slight overshoot, used for switches and pop-in effects. */
    OUT_BACK {
        @Override public float apply(float t) {
            final float c1 = 1.70158f, c3 = c1 + 1;
            float f = t - 1;
            return 1 + c3 * f * f * f + c1 * f * f;
        }
    };

    public abstract float apply(float t);
}
