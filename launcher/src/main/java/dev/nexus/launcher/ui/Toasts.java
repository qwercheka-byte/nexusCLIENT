package dev.nexus.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Short notifications in the top-right corner of the window. */
public final class Toasts {
    private static VBox container;

    private Toasts() {}

    static VBox createContainer() {
        container = new VBox(8);
        container.setAlignment(Pos.TOP_RIGHT);
        container.setPickOnBounds(false);
        container.setMouseTransparent(false);
        container.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        container.setStyle("-fx-padding: 18 20 0 0;");
        return container;
    }

    public static void info(String text) { show(text, "info", Icons.INFO, 3.5); }

    public static void success(String text) { show(text, "success", Icons.CHECK_CIRCLE, 3.5); }

    public static void error(String text) { show(text, "error", Icons.ERROR, 7); }

    private static void show(String text, String kind, String icon, double seconds) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(text, kind, icon, seconds));
            return;
        }
        if (container == null) return;
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(340);
        HBox toast = new HBox(Icons.icon(icon, 16), l);
        toast.getStyleClass().addAll("toast", kind);
        toast.setMaxWidth(400);
        toast.setOpacity(0);
        toast.setOnMouseClicked(e -> container.getChildren().remove(toast));
        container.getChildren().add(toast);
        if (container.getChildren().size() > 4) container.getChildren().remove(0);

        FadeTransition in = new FadeTransition(Duration.millis(160), toast);
        in.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(200), toast);
        slide.setFromX(24);
        slide.setToX(0);
        FadeTransition out = new FadeTransition(Duration.millis(260), toast);
        out.setToValue(0);
        SequentialTransition seq = new SequentialTransition(new ParallelTransition(in, slide), new PauseTransition(Duration.seconds(seconds)), out);
        seq.setOnFinished(e -> container.getChildren().remove(toast));
        seq.play();
    }
}
