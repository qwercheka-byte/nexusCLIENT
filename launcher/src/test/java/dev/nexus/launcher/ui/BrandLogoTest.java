package dev.nexus.launcher.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/** Checks that the bundled fingerprint really has a transparent background. */
public final class BrandLogoTest {
    public static void main(String[] args) throws Exception {
        try (InputStream in = BrandLogoTest.class.getResourceAsStream("/dev/nexus/launcher/img/brand-fingerprint.png")) {
            if (in == null) throw new AssertionError("Fingerprint resource missing");
            BufferedImage image = ImageIO.read(in);
            if (image == null || !image.getColorModel().hasAlpha()) throw new AssertionError("Fingerprint needs an alpha channel");
            if ((image.getRGB(0, 0) >>> 24) != 0 || (image.getRGB(image.getWidth() - 1, image.getHeight() - 1) >>> 24) != 0)
                throw new AssertionError("Fingerprint background is not transparent");
            int visible = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    if ((pixel >>> 24) > 32) {
                        if ((pixel & 0x00ffffff) != 0x00ffffff) throw new AssertionError("Fingerprint should be white");
                        visible++;
                    }
                }
            }
            if (visible < 500) throw new AssertionError("Fingerprint is empty");
            System.out.println("Transparent fingerprint resource verified");
        }
    }
}
