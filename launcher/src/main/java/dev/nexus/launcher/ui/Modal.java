package dev.nexus.launcher.ui;

import dev.nexus.launcher.i18n.I18n;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An in-window modal dialog: a dimmed backdrop over the whole window with a centred card. Built with
 * {@link #of(String)}, filled with content and buttons, then {@link #show()}n. Escape closes it unless
 * {@link #closable(boolean)} is false.
 */
public final class Modal {
    private static StackPane layer;

    private final VBox dialog = new VBox(14);
    private final VBox body = new VBox(12);
    private final HBox buttons = new HBox(8);
    private final StackPane backdrop = new StackPane();
    private boolean closable = true;
    private Runnable onClose;

    static void install(StackPane modalLayer) {
        layer = modalLayer;
    }

    private Modal(String title) {
        dialog.getStyleClass().add("dialog");
        dialog.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dialog.setPrefWidth(460);
        Label t = Fx.label(title, "dialog-title");
        t.setWrapText(true);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        dialog.getChildren().addAll(t, body, buttons);
        backdrop.getStyleClass().add("modal-backdrop");
        backdrop.getChildren().add(dialog);
        backdrop.setOnMouseClicked(e -> {
            if (e.getTarget() == backdrop && closable) close();
        });
        backdrop.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && closable) {
                close();
                e.consume();
            }
        });
    }

    public static Modal of(String title) {
        return new Modal(title);
    }

    public Modal width(double w) {
        dialog.setPrefWidth(w);
        return this;
    }

    public Modal text(String text) {
        Label l = Fx.label(text, "dim");
        l.setWrapText(true);
        body.getChildren().add(l);
        return this;
    }

    public Modal content(Node... nodes) {
        body.getChildren().addAll(nodes);
        return this;
    }

    public VBox body() {
        return body;
    }

    public Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        if (style != null) b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        buttons.getChildren().add(b);
        return b;
    }

    /** Button that closes the dialog (after running the optional action). */
    public Button closeButton(String text, String style, Runnable action) {
        return button(text, style, () -> {
            close();
            if (action != null) action.run();
        });
    }

    public Modal closable(boolean c) {
        closable = c;
        return this;
    }

    public Modal onClose(Runnable r) {
        onClose = r;
        return this;
    }

    public Modal show() {
        layer.getChildren().add(backdrop);
        layer.setVisible(true);
        backdrop.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(140), backdrop);
        f.setToValue(1);
        ScaleTransition s = new ScaleTransition(Duration.millis(160), dialog);
        s.setFromX(0.96);
        s.setFromY(0.96);
        s.setToX(1);
        s.setToY(1);
        f.play();
        s.play();
        backdrop.requestFocus();
        // focus the first text field, if any
        javafx.application.Platform.runLater(() -> {
            Node tf = dialog.lookup(".text-field");
            if (tf != null) tf.requestFocus();
            else backdrop.requestFocus();
        });
        return this;
    }

    public boolean isShowing() {
        return backdrop.getParent() != null;
    }

    public void close() {
        if (!isShowing()) return;
        layer.getChildren().remove(backdrop);
        if (layer.getChildren().isEmpty()) layer.setVisible(false);
        if (onClose != null) onClose.run();
    }

    // ---- common dialogs ----

    public static void confirm(String title, String text, String okText, boolean danger, Runnable onOk) {
        Modal m = of(title).text(text);
        m.closeButton(I18n.t("common.cancel"), null, null);
        m.closeButton(okText, danger ? "danger" : "primary", onOk);
        m.show();
    }

    public static void message(String title, String text) {
        Modal m = of(title).text(text);
        m.closeButton(I18n.t("common.ok"), "primary", null);
        m.show();
    }

    /**
     * Text prompt. {@code validator} returns an error message or null when the value is valid.
     */
    public static void prompt(String title, String label, String initial, Function<String, String> validator, Consumer<String> onOk) {
        Modal m = of(title);
        TextField field = new TextField(initial == null ? "" : initial);
        Label error = Fx.label("", "danger-text", "small");
        error.setManaged(false);
        error.setVisible(false);
        m.content(Fx.label(label, "dim", "small"), field, error);
        m.closeButton(I18n.t("common.cancel"), null, null);
        Button ok = m.button(I18n.t("common.ok"), "primary", () -> { });
        Runnable submit = () -> {
            String v = field.getText().trim();
            String err = validator == null ? null : validator.apply(v);
            if (err != null) {
                error.setText(err);
                error.setManaged(true);
                error.setVisible(true);
                field.getStyleClass().add("invalid");
                return;
            }
            m.close();
            onOk.accept(v);
        };
        ok.setOnAction(e -> submit.run());
        field.setOnAction(e -> submit.run());
        field.textProperty().addListener((o, a, b) -> {
            field.getStyleClass().remove("invalid");
            error.setVisible(false);
            error.setManaged(false);
        });
        m.show();
    }
}
