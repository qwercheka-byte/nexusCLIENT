package dev.nexus.client.ui.animation;

/** Global animation tuning, driven by the client settings. */
public final class AnimationSettings {
    private static float speed = 1f;

    private AnimationSettings() {}

    /** Global speed multiplier (2 = twice as fast). Values &lt;= 0.05 effectively disable animations. */
    public static float speed() {
        return speed;
    }

    public static void setSpeed(float value) {
        speed = Math.max(0.05f, value);
    }

    public static boolean instant() {
        return speed >= 9.99f;
    }
}
