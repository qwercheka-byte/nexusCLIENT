package dev.nexus.launcher.ui;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.Arrays;

/** Tests the bundled background and the platform-independent ripple field. */
public final class LiquidMotionTest {
    public static void main(String[] args) throws Exception {
        try (InputStream in = LiquidMotionTest.class.getResourceAsStream("/dev/nexus/launcher/img/backgrounds/liquid.jpg")) {
            if (in == null) throw new AssertionError("Liquid background is missing");
            var image = ImageIO.read(in);
            if (image == null || image.getWidth() < 1000 || image.getHeight() < 500)
                throw new AssertionError("Liquid background cannot be decoded");
        }
        int w = 80, h = 48;
        float[] first = new float[w * h * 2], second = new float[first.length], repeated = new float[first.length];
        LiquidMotion.fill(first, w, h, 0);
        LiquidMotion.fill(second, w, h, 3);
        LiquidMotion.fill(repeated, w, h, 0);
        if (Arrays.equals(first, second)) throw new AssertionError("Ripple field is not animated");
        if (!Arrays.equals(first, repeated)) throw new AssertionError("Ripple field is not deterministic");
        for (float value : second) {
            if (!Float.isFinite(value) || Math.abs(value) > 0.01f) throw new AssertionError("Unsafe displacement: " + value);
        }
        System.out.println("Liquid background and animation field verified");
    }
}
