package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Settings;
import javafx.animation.AnimationTimer;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animated photo background of the launcher window (same idea as the NEXUS Client main menu): a slideshow where
 * every photo slowly zooms and pans (Ken Burns effect), consecutive photos cross-fade and the picture follows the
 * mouse a little (parallax).
 * <p>
 * Photos: the user's own PNG/JPG files in {@code <data>/backgrounds/}, otherwise the photos shipped with the
 * launcher ({@code /dev/nexus/launcher/backgrounds/bg_NN.jpg}, copied from the client project at build time).
 * At most two images are loaded at a time (visible + next); loading happens in the background. The animation stops
 * while the window is minimised or hidden (e.g. during play), so it costs nothing then.
 */
public final class PhotoBackground extends Pane {
    private static final long FADE_NS = 1_600_000_000L;
    private static final long FIRST_FADE_NS = 900_000_000L;
    private static final long HOLD_NS = 9_000_000_000L;
    private static final double ZOOM = 0.12;
    private static final double PARALLAX = 0.015;
    private static final long MAX_FILE_BYTES = 40L * 1024 * 1024;
    private static final int MAX_PHOTOS = 64;

    private final ImageView[] views = {new ImageView(), new ImageView()};
    private final Slot[] slots = {new Slot(), new Slot()};
    private final Region dim = new Region();
    private final Region shade = new Region();
    private final List<String> urls = new ArrayList<>();
    private final AnimationTimer timer;
    private int front;           // slot that is fully visible (or fading out)
    private boolean fading;
    private int sequence;
    private double mouseX = 0.5;
    private double mouseY = 0.5;
    private double parX;
    private double parY;
    private long lastNs;
    private boolean enabled;
    private boolean windowVisible = true;

    public PhotoBackground() {
        getStyleClass().add("photo-background");
        setMouseTransparent(true);
        setManaged(true);
        for (ImageView v : views) {
            v.setSmooth(true);
            v.setPreserveRatio(false);
            v.setOpacity(0);
            v.setManaged(false);
        }
        dim.setManaged(false);
        shade.setManaged(false);
        shade.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(5,6,10,0.55) 0%, rgba(5,6,10,0) 28%,"
                + " rgba(5,6,10,0) 70%, rgba(5,6,10,0.65) 100%);");
        getChildren().addAll(views[0], views[1], dim, shade);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick(now);
            }
        };
    }

    /** Pauses the animation while the window is minimised or hidden. */
    public void attach(Stage stage) {
        stage.iconifiedProperty().addListener((o, a, b) -> {
            windowVisible = !b && stage.isShowing();
            updateTimer();
        });
        stage.showingProperty().addListener((o, a, b) -> {
            windowVisible = b && !stage.isIconified();
            updateTimer();
        });
        stage.sceneProperty().addListener((o, a, scene) -> hookMouse(scene));
        hookMouse(stage.getScene());
    }

    private void hookMouse(javafx.scene.Scene scene) {
        if (scene == null) return;
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
            if (scene.getWidth() > 0 && scene.getHeight() > 0) {
                mouseX = e.getSceneX() / scene.getWidth();
                mouseY = e.getSceneY() / scene.getHeight();
            }
        });
    }

    /** Re-reads the settings and the photo folder; turns the background on or off. */
    public void reload() {
        Settings s = Settings.get();
        dim.setStyle("-fx-background-color: rgba(9,10,15," + clamp(s.photoDim, 0, 0.9) + ");");
        List<String> found = s.photoBackground ? scan() : List.of();
        if (!found.equals(urls)) {
            urls.clear();
            urls.addAll(found);
            for (int i = 0; i < 2; i++) clear(i);
            fading = false;
        }
        enabled = !urls.isEmpty();
        setVisible(enabled);
        updateTimer();
    }

    public boolean active() {
        return enabled;
    }

    public static Path folder() {
        Path dir = LauncherDirs.root().resolve("backgrounds");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Log.warn("Cannot create " + dir + ": " + e.getMessage());
        }
        return dir;
    }

    private static List<String> scan() {
        TreeMap<String, String> user = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Path dir = folder();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) || !Files.isRegularFile(p)) continue;
                if (Files.size(p) > MAX_FILE_BYTES) {
                    Log.warn("Background " + p + " is larger than 40 MB, skipped");
                    continue;
                }
                user.put(p.getFileName().toString(), p.toUri().toString());
            }
        } catch (IOException e) {
            Log.warn("Cannot list " + dir + ": " + e.getMessage());
        }
        List<String> list = new ArrayList<>(user.values());
        if (list.isEmpty()) {
            for (int i = 1; i <= MAX_PHOTOS; i++) {
                URL u = PhotoBackground.class.getResource(String.format("/dev/nexus/launcher/backgrounds/bg_%02d.jpg", i));
                if (u == null) break;
                list.add(u.toExternalForm());
            }
        }
        return list.size() > MAX_PHOTOS ? new ArrayList<>(list.subList(0, MAX_PHOTOS)) : list;
    }

    private void updateTimer() {
        if (enabled && windowVisible) {
            lastNs = 0;
            timer.start();
        } else {
            timer.stop();
        }
    }

    // ---------------------------------------------------------------------------------------------- slideshow

    private void load(int slot, int index) {
        Slot s = slots[slot];
        s.index = index;
        s.sequence = sequence++;
        s.start = 0;
        s.first = false;
        s.image = new Image(urls.get(index), true);
        s.image.errorProperty().addListener((o, a, err) -> {
            if (err) Log.warn("Cannot load background " + urls.get(index) + ": " + s.image.getException());
        });
        views[slot].setImage(s.image);
        views[slot].setOpacity(0);
    }

    private void clear(int slot) {
        slots[slot].image = null;
        slots[slot].index = -1;
        views[slot].setImage(null);
        views[slot].setOpacity(0);
    }

    private static boolean ready(Slot s) {
        return s.image != null && !s.image.isError() && s.image.getProgress() >= 1.0 && s.image.getWidth() > 0;
    }

    private int nextIndex(int index) {
        int n = urls.size();
        if (n <= 1) return 0;
        if (Settings.get().photoShuffle) {
            int r = ThreadLocalRandom.current().nextInt(n - 1);
            return r >= index ? r + 1 : r;
        }
        return (index + 1) % n;
    }

    private void tick(long now) {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0 || urls.isEmpty()) return;
        long dt = lastNs == 0 ? 0 : Math.min(100_000_000L, now - lastNs);
        lastNs = now;

        Slot cur = slots[front];
        if (cur.image == null) {
            load(front, Settings.get().photoShuffle ? ThreadLocalRandom.current().nextInt(urls.size()) : 0);
            cur.first = true;
        }
        if (cur.image.isError()) {
            int idx = cur.index;
            clear(front);
            if (urls.size() > 1) load(front, nextIndex(idx));
            cur.first = true;
            return;
        }
        if (!ready(cur)) return;
        if (cur.start == 0) cur.start = now;

        int back = 1 - front;
        Slot next = slots[back];
        if (urls.size() > 1) {
            if (next.image == null) load(back, nextIndex(cur.index));
            if (next.image.isError()) {
                int idx = next.index;
                clear(back);
                load(back, nextIndex(idx));
            } else if (!fading && ready(next) && now - cur.start >= HOLD_NS) {
                fading = true;
                next.start = now;
                views[back].toFront();
                dim.toFront();
                shade.toFront();
            }
            if (fading && now - next.start >= FADE_NS) {
                clear(front);
                front = back;
                fading = false;
                cur = slots[front];
                next = slots[1 - front];
            }
        }

        // parallax, smoothed
        double tx = clamp(mouseX, 0, 1) * 2 - 1;
        double ty = clamp(mouseY, 0, 1) * 2 - 1;
        double k = 1 - Math.exp(-dt / 280_000_000.0);
        parX += (tx - parX) * k;
        parY += (ty - parY) * k;

        double curAlpha = cur.first ? Math.min(1.0, (now - cur.start) / (double) FIRST_FADE_NS) : 1.0;
        place(front, cur, w, h, now, curAlpha);
        if (fading) {
            double t = Math.min(1.0, (now - next.start) / (double) FADE_NS);
            place(1 - front, next, w, h, now, t * t * (3 - 2 * t));
        } else {
            views[1 - front].setOpacity(0);
        }
        dim.resizeRelocate(0, 0, w, h);
        shade.resizeRelocate(0, 0, w, h);
    }

    private void place(int slot, Slot s, double w, double h, long now, double alpha) {
        ImageView v = views[slot];
        double iw = s.image.getWidth();
        double ih = s.image.getHeight();
        long cycle = HOLD_NS + FADE_NS;
        long el = Math.max(0, now - s.start) % (cycle * 2);
        double p = el < cycle ? el / (double) cycle : 2.0 - el / (double) cycle;
        p = p * p * (3 - 2 * p);
        boolean zoomIn = (s.sequence & 1) == 0;
        double zoom = 1 + ZOOM * (zoomIn ? p : 1 - p);
        double angle = hash(s.sequence * 2 + 1) * Math.PI * 2;
        double cover = Math.max(w / iw, h / ih);
        double fw = iw * cover;
        double fh = ih * cover;
        v.setFitWidth(fw);
        v.setFitHeight(fh);
        v.relocate((w - fw) / 2, (h - fh) / 2);
        double scale = zoom * (1 + PARALLAX * 2);
        v.setScaleX(scale);
        v.setScaleY(scale);
        double marginX = (fw * zoom - w) / 2;
        double marginY = (fh * zoom - h) / 2;
        double f = (p * 2 - 1) * 0.9;
        v.setTranslateX(Math.cos(angle) * f * Math.max(0, marginX) - parX * PARALLAX * w);
        v.setTranslateY(Math.sin(angle) * f * Math.max(0, marginY) - parY * PARALLAX * h);
        v.setOpacity(clamp(alpha, 0, 1));
    }

    private static double hash(int n) {
        int x = n * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static final class Slot {
        Image image;
        int index = -1;
        int sequence;
        long start;
        boolean first;
    }
}
