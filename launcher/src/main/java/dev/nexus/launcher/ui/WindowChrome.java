package dev.nexus.launcher.ui;

import dev.nexus.launcher.i18n.I18n;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

/**
 * Custom window frame used instead of the white system title bar: a transparent, draggable strip over the top of
 * the window with themed minimise / maximise / close buttons, plus edge resizing, double-click and drag-to-top
 * maximising (the stage is {@code UNDECORATED}, so all of this has to be done by hand).
 * <p>
 * Maximising is implemented manually (the window is sized to the visual bounds of its screen) because an
 * undecorated JavaFX stage would otherwise cover the Windows taskbar.
 */
final class WindowChrome {
    static final double HEIGHT = 34;
    private static final double EDGE = 6;
    private static final double DRAG_THRESHOLD = 4;

    private final Stage stage;
    private final StackPane root;
    private final HBox bar = new HBox();
    private final SVGPath maxGlyph = glyph("");
    private final Tooltip maxTip = new Tooltip();
    private final double minW, minH;

    private boolean maximized;
    private Rectangle2D restoreBounds;

    // title bar drag
    private double pressScreenX, pressScreenY, offsetX, offsetY;
    private boolean dragging, pressedOnBar;

    // edge resize
    private Cursor edge = Cursor.DEFAULT;
    private boolean resizing;
    private double startX, startY, startW, startH, startScreenX, startScreenY;

    WindowChrome(Stage stage, StackPane root, double minW, double minH) {
        this.stage = stage;
        this.root = root;
        this.minW = minW;
        this.minH = minH;

        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.TOP_RIGHT);
        bar.setMinHeight(HEIGHT);
        bar.setPrefHeight(HEIGHT);
        bar.setMaxHeight(HEIGHT);
        StackPane.setAlignment(bar, Pos.TOP_CENTER);

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        grow.setMouseTransparent(true);

        StackPane min = button("win-btn", glyph("M0 5.5 H10"), I18n.t("window.minimize"), () -> stage.setIconified(true));
        StackPane max = button("win-btn", maxGlyph, null, this::toggleMaximized);
        Tooltip.install(max, maxTip);
        maxTip.setShowDelay(Duration.millis(600));
        StackPane close = button("win-btn", glyph("M0.5 0.5 L9.5 9.5 M9.5 0.5 L0.5 9.5"), I18n.t("window.close"), this::requestClose);
        close.getStyleClass().add("close");
        bar.getChildren().addAll(grow, min, max, close);
        updateState();

        bar.addEventHandler(MouseEvent.MOUSE_PRESSED, this::barPressed);
        bar.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::barDragged);
        bar.addEventHandler(MouseEvent.MOUSE_RELEASED, this::barReleased);
        bar.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && e.getTarget() == bar) toggleMaximized();
        });
    }

    /** The title bar node; add it as the top-most child of the root stack. */
    HBox bar() {
        return bar;
    }

    /** Installs edge resizing on the scene (call once the scene exists). */
    void attach(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> updateEdgeCursor(scene, e));
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, e -> {
            if (!resizing && e.getTarget() == scene) setEdge(scene, Cursor.DEFAULT);
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (edge == Cursor.DEFAULT || maximized || e.getButton() != MouseButton.PRIMARY) return;
            resizing = true;
            startX = stage.getX();
            startY = stage.getY();
            startW = stage.getWidth();
            startH = stage.getHeight();
            startScreenX = e.getScreenX();
            startScreenY = e.getScreenY();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!resizing) return;
            resize(e.getScreenX() - startScreenX, e.getScreenY() - startScreenY);
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!resizing) return;
            resizing = false;
            updateEdgeCursor(scene, e);
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (edge != Cursor.DEFAULT && !maximized) e.consume();
        });
    }

    boolean isMaximized() {
        return maximized;
    }

    /** Size of the window when it is not maximised (what should be remembered between starts). */
    Rectangle2D normalBounds() {
        return maximized && restoreBounds != null ? restoreBounds
                : new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    void toggleMaximized() {
        if (maximized) restore(); else maximize();
    }

    void maximize() {
        if (maximized) return;
        restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Screen screen = screen();
        Rectangle2D vb = screen.getVisualBounds();
        double h = vb.getHeight();
        // No visible taskbar (auto-hide or none): leave one pixel free at the bottom. Otherwise the auto-hidden taskbar
        // can no longer be revealed, and a borderless window covering the whole screen is treated as fullscreen by
        // some Direct3D implementations (it crashed the renderer under Wine when restoring).
        if (vb.equals(screen.getBounds())) h -= 1;
        stage.setX(vb.getMinX());
        stage.setY(vb.getMinY());
        stage.setWidth(vb.getWidth());
        stage.setHeight(h);
        maximized = true;
        updateState();
    }

    void restore() {
        if (!maximized) return;
        maximized = false;
        if (restoreBounds != null) {
            stage.setWidth(restoreBounds.getWidth());
            stage.setHeight(restoreBounds.getHeight());
            stage.setX(restoreBounds.getMinX());
            stage.setY(restoreBounds.getMinY());
        }
        updateState();
    }

    // ---- title bar dragging ----

    private void barPressed(MouseEvent e) {
        pressedOnBar = e.getButton() == MouseButton.PRIMARY && e.getTarget() == bar && !resizing;
        if (!pressedOnBar) return;
        pressScreenX = e.getScreenX();
        pressScreenY = e.getScreenY();
        offsetX = e.getScreenX() - stage.getX();
        offsetY = e.getScreenY() - stage.getY();
        dragging = false;
    }

    private void barDragged(MouseEvent e) {
        if (!pressedOnBar || resizing) return;
        if (!dragging) {
            if (Math.abs(e.getScreenX() - pressScreenX) < DRAG_THRESHOLD && Math.abs(e.getScreenY() - pressScreenY) < DRAG_THRESHOLD) return;
            dragging = true;
            if (maximized) {
                // un-maximise under the cursor, keeping the grab point at the same relative position
                double ratio = offsetX / stage.getWidth();
                restore();
                offsetX = stage.getWidth() * ratio;
                offsetY = Math.min(offsetY, HEIGHT / 2);
            }
        }
        stage.setX(e.getScreenX() - offsetX);
        stage.setY(e.getScreenY() - offsetY);
    }

    private void barReleased(MouseEvent e) {
        if (!pressedOnBar) return;
        pressedOnBar = false;
        if (dragging) {
            dragging = false;
            // dropping the window at the top edge of a screen maximises it, like Windows snap
            Rectangle2D vb = screenAt(e.getScreenX(), e.getScreenY()).getVisualBounds();
            if (stage.getY() < vb.getMinY()) stage.setY(vb.getMinY()); // keep the title bar reachable (and the restore position sane)
            if (e.getScreenY() <= vb.getMinY() + 2) maximize();
        }
    }

    // ---- edge resizing ----

    private void updateEdgeCursor(Scene scene, MouseEvent e) {
        if (resizing) return;
        if (maximized) {
            setEdge(scene, Cursor.DEFAULT);
            return;
        }
        double x = e.getSceneX(), y = e.getSceneY(), w = scene.getWidth(), h = scene.getHeight();
        boolean l = x < EDGE, r = x > w - EDGE, t = y < EDGE, b = y > h - EDGE;
        Cursor c = t && l ? Cursor.NW_RESIZE : t && r ? Cursor.NE_RESIZE : b && l ? Cursor.SW_RESIZE : b && r ? Cursor.SE_RESIZE
                : l ? Cursor.W_RESIZE : r ? Cursor.E_RESIZE : t ? Cursor.N_RESIZE : b ? Cursor.S_RESIZE : Cursor.DEFAULT;
        setEdge(scene, c);
    }

    private void setEdge(Scene scene, Cursor c) {
        if (c == edge) return;
        edge = c;
        scene.setCursor(c == Cursor.DEFAULT ? null : c);
    }

    private void resize(double dx, double dy) {
        double x = startX, y = startY, w = startW, h = startH;
        if (edge == Cursor.E_RESIZE || edge == Cursor.NE_RESIZE || edge == Cursor.SE_RESIZE) w = Math.max(minW, startW + dx);
        if (edge == Cursor.S_RESIZE || edge == Cursor.SE_RESIZE || edge == Cursor.SW_RESIZE) h = Math.max(minH, startH + dy);
        if (edge == Cursor.W_RESIZE || edge == Cursor.NW_RESIZE || edge == Cursor.SW_RESIZE) {
            w = Math.max(minW, startW - dx);
            x = startX + startW - w;
        }
        if (edge == Cursor.N_RESIZE || edge == Cursor.NW_RESIZE || edge == Cursor.NE_RESIZE) {
            h = Math.max(minH, startH - dy);
            y = startY + startH - h;
        }
        stage.setX(x);
        stage.setY(y);
        stage.setWidth(w);
        stage.setHeight(h);
    }

    // ---- helpers ----

    private void requestClose() {
        MainWindow.get().requestExit();
    }

    private void updateState() {
        maxGlyph.setContent(maximized
                ? "M2.5 0.5 H9.5 V7.5 M0.5 2.5 H7.5 V9.5 H0.5 Z"
                : "M0.5 0.5 H9.5 V9.5 H0.5 Z");
        maxTip.setText(I18n.t(maximized ? "window.restore" : "window.maximize"));
        // thin outline instead of the system border/shadow, hidden while maximised
        if (maximized) root.getStyleClass().remove("framed");
        else if (!root.getStyleClass().contains("framed")) root.getStyleClass().add("framed");
    }

    private Screen screen() {
        List<Screen> s = Screen.getScreensForRectangle(stage.getX() + stage.getWidth() / 2, stage.getY() + HEIGHT / 2, 1, 1);
        return s.isEmpty() ? Screen.getPrimary() : s.get(0);
    }

    private static Screen screenAt(double x, double y) {
        List<Screen> s = Screen.getScreensForRectangle(x, y, 1, 1);
        return s.isEmpty() ? Screen.getPrimary() : s.get(0);
    }

    private static SVGPath glyph(String path) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.getStyleClass().add("win-glyph");
        return p;
    }

    private static StackPane button(String style, SVGPath glyph, String tip, Runnable action) {
        StackPane b = new StackPane(glyph);
        b.getStyleClass().add(style);
        b.setMinSize(46, HEIGHT);
        b.setPrefSize(46, HEIGHT);
        b.setMaxSize(46, HEIGHT);
        if (tip != null) {
            Tooltip t = new Tooltip(tip);
            t.setShowDelay(Duration.millis(600));
            Tooltip.install(b, t);
        }
        b.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) action.run();
        });
        return b;
    }
}
