package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.core.Tasks;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Small UI helpers. */
public final class Fx {
    private Fx() {}

    /** Runs {@code work} on a background thread and delivers the result (or error) on the FX thread. */
    public static <T> void async(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Tasks.io().execute(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                if (!(t instanceof InterruptedIOException)) Log.warn("Background task failed: " + t, t);
                Platform.runLater(() -> onError.accept(t));
            }
        });
    }

    public static void async(Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        async(() -> {
            work.run();
            return null;
        }, x -> onSuccess.run(), onError);
    }

    public static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    public static Button button(String text, String icon, String... styles) {
        Button b = new Button(text);
        if (icon != null) b.setGraphic(Icons.icon(icon, 16));
        b.getStyleClass().addAll(styles);
        return b;
    }

    public static Button iconButton(String icon, String tooltip, String... styles) {
        Button b = new Button();
        b.setGraphic(Icons.icon(icon, 17));
        b.getStyleClass().add("icon-only");
        b.getStyleClass().addAll(styles);
        if (tooltip != null) tip(b, tooltip);
        return b;
    }

    public static void tip(javafx.scene.control.Control c, String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(350));
        c.setTooltip(t);
    }

    public static CheckBox toggle(boolean selected) {
        CheckBox c = new CheckBox();
        c.getStyleClass().add("switch");
        c.setSelected(selected);
        return c;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox row(double spacing, Node... nodes) {
        HBox h = new HBox(spacing, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    public static VBox column(double spacing, Node... nodes) {
        return new VBox(spacing, nodes);
    }

    public static Region accentLine() {
        Region r = new Region();
        r.getStyleClass().add("accent-line");
        return r;
    }

    /** Coloured tile with the first letter of a name (instances, offline avatars). */
    public static StackPane tile(String name, String color, double size) {
        StackPane p = new StackPane();
        p.getStyleClass().addAll("instance-tile", "tile-" + color);
        p.setMinSize(size, size);
        p.setPrefSize(size, size);
        p.setMaxSize(size, size);
        String letter = name == null || name.isBlank() ? "?" : name.trim().substring(0, 1).toUpperCase();
        Label l = new Label(letter);
        l.getStyleClass().add("instance-tile-letter");
        l.setStyle("-fx-font-size: " + Math.round(size * 0.45) + "px;");
        p.getChildren().add(l);
        return p;
    }

    public static VBox emptyState(String icon, String title, String text) {
        VBox v = new VBox(Icons.icon(icon, 40), label(title, "h2"), label(text, "dim"));
        v.getStyleClass().add("empty-state");
        ((Label) v.getChildren().get(2)).setWrapText(true);
        ((Label) v.getChildren().get(2)).setMaxWidth(420);
        ((Label) v.getChildren().get(2)).setAlignment(Pos.CENTER);
        ((Label) v.getChildren().get(2)).setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return v;
    }

    /** Opens a folder or file with the system file manager / default application. */
    public static void openPath(Path path) {
        Tasks.io().execute(() -> {
            try {
                Files.createDirectories(Files.isDirectory(path) || !Files.exists(path) ? path : path.getParent());
                ProcessBuilder pb = switch (OS.TYPE) {
                    case WINDOWS -> new ProcessBuilder("explorer.exe", path.toString());
                    case MACOS -> new ProcessBuilder("open", path.toString());
                    default -> new ProcessBuilder("xdg-open", path.toString());
                };
                pb.start();
            } catch (Exception e) {
                Log.warn("Cannot open " + path + ": " + e.getMessage());
                Platform.runLater(() -> Toasts.error(dev.nexus.launcher.i18n.I18n.t("error.openFolder", path)));
            }
        });
    }

    public static String duration(long seconds) {
        if (seconds < 60) return dev.nexus.launcher.i18n.I18n.t("time.lessMinute");
        long h = seconds / 3600, m = (seconds % 3600) / 60;
        return h > 0 ? dev.nexus.launcher.i18n.I18n.t("time.hm", h, m) : dev.nexus.launcher.i18n.I18n.t("time.m", m);
    }

    public static String errorText(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && (c.getMessage() == null || c instanceof java.util.concurrent.ExecutionException)) c = c.getCause();
        String m = c.getMessage();
        if (c instanceof java.net.UnknownHostException || c instanceof java.net.ConnectException
                || c instanceof java.net.http.HttpConnectTimeoutException) {
            return dev.nexus.launcher.i18n.I18n.t("error.network", m == null ? c.getClass().getSimpleName() : m);
        }
        return m == null || m.isBlank() ? c.getClass().getSimpleName() : m;
    }
}
