package dev.nexus.launcher.ui;

import javafx.application.Platform;
import javafx.scene.effect.DisplacementMap;
import javafx.scene.effect.FloatMap;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Renders two ripple frames on JavaFX to verify that the actual image moves. */
public final class LiquidBackgroundFxTest {
    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                Image image = new Image(LiquidBackgroundFxTest.class.getResource(
                        "/dev/nexus/launcher/img/backgrounds/liquid.jpg").toExternalForm(), 400, 232, true, true);
                ImageView view = new ImageView(image);
                int w = 80, h = 48;
                FloatMap map = new FloatMap(w, h);
                DisplacementMap effect = new DisplacementMap(map);
                effect.setWrap(true);
                view.setEffect(effect);
                setFrame(map, w, h, 0);
                WritableImage first = view.snapshot(null, null);
                setFrame(map, w, h, 4);
                WritableImage second = view.snapshot(null, null);
                int different = 0;
                for (int y = 0; y < (int) first.getHeight(); y += 3) {
                    for (int x = 0; x < (int) first.getWidth(); x += 3) {
                        if (first.getPixelReader().getArgb(x, y) != second.getPixelReader().getArgb(x, y)) different++;
                    }
                }
                if (different < 100) throw new AssertionError("Liquid effect did not change the rendered image: " + different);
                System.out.println("JavaFX liquid effect changes the rendered image (" + different + " pixels)");
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) throw new AssertionError("JavaFX test timed out");
        Platform.exit();
        if (failure.get() != null) throw new AssertionError("Liquid rendering failed", failure.get());
    }

    private static void setFrame(FloatMap map, int w, int h, double seconds) {
        float[] samples = new float[w * h * 2];
        LiquidMotion.fill(samples, w, h, seconds);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 2;
                map.setSamples(x, y, samples[i], samples[i + 1]);
            }
        }
    }
}
