package dev.nexus.launcher.ui;

import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.net.Progress;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Bottom bar: the selected instance, preparation progress and the Play / Cancel / Stop button. */
final class PlayBar extends HBox {
    private final MainWindow window;
    private final StackPane tileHolder = new StackPane();
    private final Label name = Fx.label("", "h3");
    private final Label info = Fx.label("", "dim", "small");
    private final VBox progressBox = new VBox(5);
    private final Label stage = Fx.label("", "small");
    private final Label detail = Fx.label("", "faint", "tiny");
    private final ProgressBar bar = new ProgressBar(0);
    private final Button play = new Button();
    private Instance shown;
    private boolean initialized;

    PlayBar(MainWindow window) {
        this.window = window;
        getStyleClass().add("play-bar");
        setMinHeight(76);

        VBox text = new VBox(2, name, info);
        text.setMinWidth(160);
        HBox left = new HBox(12, tileHolder, text);
        left.setAlignment(Pos.CENTER_LEFT);

        bar.setMaxWidth(Double.MAX_VALUE);
        detail.setMaxWidth(520);
        progressBox.getChildren().addAll(new HBox(stage), bar, detail);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBox, Priority.ALWAYS);
        progressBox.setMaxWidth(560);

        play.getStyleClass().setAll("button", "play-button");
        play.setMinWidth(170);
        play.setOnAction(e -> onPlay());

        getChildren().addAll(left, Fx.spacer(), progressBox, play);

        LaunchController.addListener(this::refresh);
        Timeline poll = new Timeline(new KeyFrame(Duration.millis(100), e -> tick()));
        poll.setCycleCount(Animation.INDEFINITE);
        poll.play();
        refresh();
    }

    private void onPlay() {
        Instance inst = window.selectedInstance();
        if (inst == null) {
            window.showInstances();
            CreateInstanceDialog.show(window);
            return;
        }
        if (LaunchController.isPreparing(inst)) {
            LaunchController.cancel();
        } else if (GameProcess.running(inst) != null) {
            Modal.confirm(I18n.t("play.stopTitle"), I18n.t("play.stopText", inst.name), I18n.t("play.stop"), true,
                    () -> LaunchController.stop(inst));
        } else {
            LaunchController.launch(inst);
        }
    }

    void refresh() {
        Instance inst = window.selectedInstance();
        if (!initialized || inst != shown || inst != null && !name.getText().equals(inst.name)) {
            initialized = true;
            shown = inst;
            tileHolder.getChildren().setAll(inst == null ? Fx.tile("+", "slate", 44) : Fx.tile(inst.name, inst.color, 44));
            name.setText(inst == null ? I18n.t("play.noInstance") : inst.name);
        }
        if (inst != null) info.setText(inst.describe());
        else info.setText(I18n.t("play.noInstanceHint"));
        tick();
    }

    private void tick() {
        Instance inst = shown;
        Instance prep = LaunchController.preparing();
        Progress p = LaunchController.progress();
        boolean preparingThis = inst != null && LaunchController.isPreparing(inst);
        GameProcess gp = inst == null ? null : GameProcess.running(inst);

        if (prep != null && p != null) {
            progressBox.setVisible(true);
            String st = p.stage() == null ? I18n.t("stage.starting") : p.stage();
            stage.setText(preparingThis ? st : prep.name + " - " + st);
            String d = p.detail();
            detail.setText(d == null ? "" : d);
            double f = p.fraction();
            bar.setProgress(f < 0 ? ProgressBar.INDETERMINATE_PROGRESS : f);
        } else {
            progressBox.setVisible(false);
        }

        String state;
        if (preparingThis) state = "cancel";
        else if (gp != null) state = "running";
        else state = "idle";
        if (!state.equals(play.getUserData())) {
            play.setUserData(state);
            play.getStyleClass().removeAll("cancel", "running");
            switch (state) {
                case "cancel" -> {
                    play.setText(I18n.t("play.cancel"));
                    play.setGraphic(Icons.icon(Icons.CLOSE, 17));
                    play.getStyleClass().add("cancel");
                }
                case "running" -> {
                    play.setText(I18n.t("play.stop"));
                    play.setGraphic(Icons.icon(Icons.STOP, 17));
                    play.getStyleClass().add("running");
                }
                default -> {
                    play.setText(I18n.t(inst == null ? "play.create" : "play.play"));
                    play.setGraphic(Icons.icon(inst == null ? Icons.ADD : Icons.PLAY, 20));
                }
            }
        }
        if ("running".equals(state)) {
            long s = (System.currentTimeMillis() - gp.startedAt()) / 1000;
            info.setText(I18n.t("play.runningFor", String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)));
        } else if (inst != null && !info.getText().equals(inst.describe())) {
            info.setText(inst.describe());
        }
        play.setDisable(state.equals("idle") && inst != null && prep != null);
    }
}
