package dev.nexus.launcher.ui;

import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.game.LogLine;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/** Live game output (or the output of the last session when the game is not running). */
final class ConsoleTab extends VBox implements InstancePage.Tab {
    private final Instance inst;
    private final ObservableList<LogLine> lines = FXCollections.observableArrayList();
    private final FilteredList<LogLine> filtered = new FilteredList<>(lines);
    private final ListView<LogLine> view = new ListView<>(filtered);
    private final ConcurrentLinkedQueue<LogLine> pending = new ConcurrentLinkedQueue<>();
    private final CheckBox autoScroll = new CheckBox(I18n.t("console.autoscroll"));
    private final TextField filter = new TextField();
    private final Label state = Fx.label("", "badge");
    private final Button stop = Fx.button(I18n.t("play.stop"), Icons.STOP, "danger");
    private final Timeline flush;
    private GameProcess attached;
    private final GameProcess.Listener listener = new GameProcess.Listener() {
        @Override
        public void onLine(LogLine line) {
            pending.add(line);
        }

        @Override
        public void onExit(int exitCode, Path crashReport) {
            if (crashReport != null) pending.add(new LogLine(LogLine.Level.ERROR, I18n.t("console.crashReport", crashReport)));
        }
    };
    private int prepareIndex;
    private boolean showingLastSession;

    ConsoleTab(MainWindow window, Instance inst) {
        super(10);
        this.inst = inst;

        filter.setPromptText(I18n.t("console.filter"));
        filter.setPrefWidth(220);
        filter.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(q.isEmpty() ? null : l -> l.text().toLowerCase(Locale.ROOT).contains(q));
        });
        autoScroll.setSelected(true);
        Button copy = Fx.iconButton(Icons.COPY, I18n.t("console.copy"));
        copy.setOnAction(e -> copy(view.getSelectionModel().getSelectedItems().size() > 1
                ? new ArrayList<>(view.getSelectionModel().getSelectedItems()) : new ArrayList<>(filtered)));
        Button clear = Fx.iconButton(Icons.DELETE, I18n.t("console.clear"));
        clear.setOnAction(e -> lines.clear());
        Button logs = Fx.iconButton(Icons.FOLDER, I18n.t("console.openLogs"));
        logs.setOnAction(e -> Fx.openPath(inst.gameDir().resolve("logs")));
        stop.setOnAction(e -> LaunchController.stop(inst));
        HBox toolbar = new HBox(8, state, filter, autoScroll, Fx.spacer(), stop, copy, clear, logs);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        view.getStyleClass().add("console");
        view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        view.setFixedCellSize(18);
        view.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(LogLine l, boolean empty) {
                super.updateItem(l, empty);
                getStyleClass().removeAll("log-warn", "log-error", "log-debug", "log-launcher");
                if (empty || l == null) {
                    setText(null);
                    return;
                }
                setText(l.text());
                switch (l.level()) {
                    case WARN -> getStyleClass().add("log-warn");
                    case ERROR -> getStyleClass().add("log-error");
                    case DEBUG -> getStyleClass().add("log-debug");
                    case LAUNCHER -> getStyleClass().add("log-launcher");
                    default -> { }
                }
            }
        });
        view.setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.C) copy(new ArrayList<>(view.getSelectionModel().getSelectedItems()));
        });
        view.setPlaceholder(Fx.label(I18n.t("console.empty"), "faint"));
        VBox.setVgrow(view, Priority.ALWAYS);
        getChildren().addAll(toolbar, view);

        flush = new Timeline(new KeyFrame(Duration.millis(100), e -> drain()));
        flush.setCycleCount(Animation.INDEFINITE);
    }

    @Override
    public void onShow() {
        flush.play();
        sync();
    }

    @Override
    public void onHide() {
        flush.stop();
        if (attached != null) attached.removeListener(listener);
        attached = null;
    }

    @Override
    public void onLaunchStateChanged() {
        sync();
    }

    /** Attaches to a newly started process, or shows the last session log when idle. */
    private void sync() {
        GameProcess gp = GameProcess.running(inst);
        boolean preparing = LaunchController.isPreparing(inst);
        if (gp != null && gp != attached) {
            if (attached != null) attached.removeListener(listener);
            attached = gp;
            pending.clear();
            if (showingLastSession) lines.clear();
            showingLastSession = false;
            gp.addListener(listener, replay -> pending.addAll(replay));
        } else if (gp == null && attached != null && !attached.isAlive()) {
            attached.removeListener(listener);
            attached = null;
        }
        if (preparing && prepareIndex == 0) {
            lines.clear();
            showingLastSession = false;
        }
        if (gp == null && !preparing && attached == null && lines.isEmpty()) loadLastSession();

        stop.setVisible(gp != null);
        stop.setManaged(gp != null);
        state.getStyleClass().removeAll("badge-success", "badge-warning");
        if (gp != null) {
            state.setText(I18n.t("console.state.running"));
            state.getStyleClass().add("badge-success");
        } else if (preparing) {
            state.setText(I18n.t("console.state.preparing"));
            state.getStyleClass().add("badge-warning");
        } else {
            state.setText(showingLastSession ? I18n.t("console.state.last") : I18n.t("console.state.idle"));
        }
        if (!preparing) prepareIndex = 0;
    }

    private void loadLastSession() {
        Path file = inst.dir.resolve("logs").resolve("launcher-output.log");
        if (!Files.isRegularFile(file)) return;
        Fx.async(() -> {
            ArrayDeque<LogLine> tail = new ArrayDeque<>();
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String s;
                while ((s = r.readLine()) != null) {
                    tail.addLast(new LogLine(LogLine.guess(s), s));
                    if (tail.size() > GameProcess.BUFFER_LINES) tail.removeFirst();
                }
            } catch (java.nio.charset.MalformedInputException e) {
                // detached sessions are written in the platform charset
                tail.clear();
                for (String s : Files.readAllLines(file, java.nio.charset.Charset.defaultCharset())) {
                    tail.addLast(new LogLine(LogLine.guess(s), s));
                    if (tail.size() > GameProcess.BUFFER_LINES) tail.removeFirst();
                }
            }
            return new ArrayList<>(tail);
        }, (List<LogLine> list) -> {
            if (attached != null || !lines.isEmpty() || list.isEmpty()) return;
            lines.add(new LogLine(LogLine.Level.LAUNCHER, I18n.t("console.lastSession", file)));
            lines.addAll(list);
            showingLastSession = true;
            state.setText(I18n.t("console.state.last"));
            if (autoScroll.isSelected()) view.scrollTo(filtered.size() - 1);
        }, err -> { });
    }

    private void drain() {
        List<LogLine> batch = new ArrayList<>();
        LogLine l;
        while ((l = pending.poll()) != null) batch.add(l);
        if (LaunchController.isPreparing(inst)) {
            List<LogLine> prep = LaunchController.prepareLog(inst);
            if (prep.size() > prepareIndex) {
                batch.addAll(prep.subList(prepareIndex, prep.size()));
                prepareIndex = prep.size();
            }
        }
        if (batch.isEmpty()) return;
        lines.addAll(batch);
        int overflow = lines.size() - GameProcess.BUFFER_LINES;
        if (overflow > 0) lines.remove(0, overflow);
        if (autoScroll.isSelected() && !filtered.isEmpty()) view.scrollTo(filtered.size() - 1);
    }

    private void copy(List<LogLine> list) {
        if (list.isEmpty()) return;
        ClipboardContent c = new ClipboardContent();
        c.putString(list.stream().map(LogLine::text).collect(Collectors.joining(System.lineSeparator())));
        Clipboard.getSystemClipboard().setContent(c);
        Toasts.info(I18n.t("console.copied", list.size()));
    }
}
