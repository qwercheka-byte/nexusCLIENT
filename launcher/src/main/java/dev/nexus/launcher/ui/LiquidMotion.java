package dev.nexus.launcher.ui;

/** Small, smoothly varying displacement field used to animate the liquid background. */
final class LiquidMotion {
    private LiquidMotion() {}

    /** Writes normalised x/y displacements, interleaved, into a reusable array. */
    static void fill(float[] samples, int width, int height, double seconds) {
        if (width < 2 || height < 2 || samples.length != width * height * 2)
            throw new IllegalArgumentException("Invalid displacement field");
        for (int y = 0; y < height; y++) {
            double v = y / (double) (height - 1);
            for (int x = 0; x < width; x++) {
                double u = x / (double) (width - 1);
                int i = (y * width + x) * 2;
                samples[i] = (float) (0.006 * Math.sin(v * 8.0 + seconds * 0.55)
                        + 0.003 * Math.sin((u + v) * 9.0 - seconds * 0.38));
                samples[i + 1] = (float) (0.006 * Math.sin(u * 7.0 - seconds * 0.48)
                        + 0.003 * Math.cos((u - v) * 8.0 + seconds * 0.35));
            }
        }
    }
}
