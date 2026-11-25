package dev.nexus.recovery;

/** Display positions and targets are separate: only the targets are persisted by the client. */
public final class HudMotion {
    private static final int[] ACTIVE = {0, 4, 3};
    // The center box encloses the notification actually drawn in 1.0.34.
    private static final float[][] BOX = {
        {18, 19, 312, 66}, null, null, {576, 421, 136, 23}, {1000, 39, 263, 321}
    };
    private final float[][] visual = new float[5][2];
    private boolean initialized, wasDown, dirty;
    private int drag = -1;
    private float grabX, grabY;
    private long lastNanos;

    public void step(float[][] target, boolean chat, boolean down, double mouseX, double mouseY,
                     long now, float speed, boolean instant) {
        if (!initialized) {
            for (int i = 0; i < visual.length; i++) {
                visual[i][0] = target[i][0]; visual[i][1] = target[i][1];
            }
            initialized = true;
            lastNanos = now;
        }
        double dt = Math.max(0, Math.min(0.1, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;

        if (!chat || !down) {
            if (drag >= 0) dirty = true;
            drag = -1;
        } else if (drag < 0 && !wasDown) {
            // Later entries are visually on top of earlier entries.
            for (int p = ACTIVE.length - 1; p >= 0; p--) {
                int i = ACTIVE[p]; float[] b = BOX[i];
                double x = b[0] + visual[i][0], y = b[1] + visual[i][1];
                if (mouseX >= x && mouseX <= x + b[2] && mouseY >= y && mouseY <= y + b[3]) {
                    drag = i; grabX = (float)(mouseX - x); grabY = (float)(mouseY - y);
                    // Start from where the user grabbed, even if the element was still moving.
                    target[i][0] = visual[i][0]; target[i][1] = visual[i][1];
                    break;
                }
            }
        }
        if (chat && down && drag >= 0) {
            float[] b = BOX[drag];
            target[drag][0] = clamp((float) mouseX - grabX - b[0], 2 - b[0], 1280 - b[0] - b[2] - 2);
            target[drag][1] = clamp((float) mouseY - grabY - b[1], 2 - b[1], 720 - b[1] - b[3] - 2);
        }
        wasDown = down;
        float k = instant ? 1 : (float)(1 - Math.exp(-18 * Math.max(0.05f, speed) * dt));
        for (int i : ACTIVE) for (int axis = 0; axis < 2; axis++) {
            visual[i][axis] += (target[i][axis] - visual[i][axis]) * k;
            if (Math.abs(target[i][axis] - visual[i][axis]) < 0.0005f) visual[i][axis] = target[i][axis];
        }
    }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    public float position(int zone, int axis) { return visual[zone][axis]; }
    public float[] box(int zone) { return BOX[zone]; }
    public int[] active() { return ACTIVE; }
    public boolean takeDirty() { boolean result = dirty; dirty = false; return result; }
}
