package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.net.Progress;
import dev.nexus.launcher.update.NexusClientStore;
import dev.nexus.launcher.update.Updates;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bar above the page area that announces updates. Nothing is downloaded until the user presses "Update":
 * then new NEXUS Client jars are installed into the instances and the new launcher jar is downloaded; the
 * launcher restarts only after a second confirmation.
 */
final class UpdateBanner extends HBox {
    private final MainWindow window;
    private final Label title = Fx.label("", "update-title");
    private final Label detail = Fx.label("", "dim", "small");
    private final ProgressBar bar = new ProgressBar(0);
    private final Button notes = Fx.button(I18n.t("update.notes"), null, "ghost");
    private final Button action = Fx.button("", Icons.DOWNLOAD, "primary");
    private final Button close = Fx.iconButton(Icons.CLOSE, I18n.t("update.later"), "ghost");
    private Updates.Check check;
    private Path launcherJar;
    private Timeline poll;

    UpdateBanner(MainWindow window) {
        this.window = window;
        getStyleClass().add("update-banner");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        var icon = Icons.icon(Icons.DOWNLOAD, 16);
        icon.getStyleClass().add("update-icon");
        StackPane badge = new StackPane(icon);
        badge.getStyleClass().add("update-badge");

        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setVisible(false);
        bar.setManaged(false);
        VBox text = new VBox(3, title, detail, bar);
        HBox.setHgrow(text, Priority.ALWAYS);

        notes.setOnAction(e -> showNotes());
        close.setOnAction(e -> hideBanner());
        getChildren().addAll(badge, text, notes, action, close);
        hideBanner();
    }

    // ---- states ----

    /** Shows what is available. Does nothing while an update is being installed or waits for a restart. */
    void offer(Updates.Check c) {
        if (isWorking() || launcherJar != null) return;
        if (!c.any()) {
            hideBanner();
            return;
        }
        check = c;
        List<String> parts = new ArrayList<>();
        if (c.launcherUpdate()) parts.add(I18n.t("update.launcherPart", BuildInfo.VERSION, c.launcherVersion()));
        if (c.clientUpdate()) parts.add(I18n.t("update.clientPart", c.clientVersion()));
        title.setText(I18n.t("update.available"));
        detail.setText(String.join("   ·   ", parts));
        notes.setVisible(true);
        notes.setManaged(true);
        boolean manualOnly = !c.clientUpdate() && launcherManual(c) != null;
        if (manualOnly) {
            action.setText(I18n.t("update.download"));
            action.setOnAction(e -> openPage());
            detail.setText(detail.getText() + "   ·   " + launcherManual(c));
        } else {
            action.setText(I18n.t("update.install"));
            action.setOnAction(e -> install());
        }
        action.setDisable(false);
        close.setDisable(false);
        showBanner();
    }

    private void readyToRestart(String version) {
        title.setText(I18n.t("update.ready", version));
        detail.setText(I18n.t("update.readyText"));
        notes.setVisible(false);
        notes.setManaged(false);
        action.setText(I18n.t("update.restart"));
        action.setOnAction(e -> restart());
        action.setDisable(false);
        close.setDisable(false);
        showBanner();
    }

    private void manualLauncher(Updates.Check c) {
        title.setText(I18n.t("update.launcherManualTitle", c.launcherVersion()));
        detail.setText(launcherManual(c));
        notes.setVisible(true);
        notes.setManaged(true);
        action.setText(I18n.t("update.download"));
        action.setOnAction(e -> openPage());
        action.setDisable(false);
        close.setDisable(false);
        showBanner();
    }

    /** Reason why the launcher update must be installed by hand, or null when it installs itself. */
    private static String launcherManual(Updates.Check c) {
        if (!c.launcherUpdate()) return null;
        if (c.launcherFile() == null) return I18n.t("update.noBuild", Updates.platformKey());
        String blocker = Updates.selfUpdateBlocker();
        return blocker == null ? null : I18n.t("update.blocker." + blocker);
    }

    // ---- actions ----

    private void install() {
        Updates.Check c = check;
        boolean launcher = c.launcherUpdate() && launcherManual(c) == null;
        Progress progress = new Progress();
        action.setDisable(true);
        close.setDisable(true);
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setVisible(true);
        bar.setManaged(true);
        title.setText(I18n.t("update.installing"));
        poll = new Timeline(new KeyFrame(Duration.millis(120), e -> {
            double f = progress.fraction();
            bar.setProgress(f < 0 ? ProgressBar.INDETERMINATE_PROGRESS : f);
            if (progress.detail() != null) detail.setText(progress.detail() + (f >= 0 ? String.format("  ·  %.0f%%", f * 100) : ""));
        }));
        poll.setCycleCount(Animation.INDEFINITE);
        poll.play();

        Fx.async(() -> {
            NexusClientStore.SyncResult sync = c.clientUpdate() ? Updates.installClient(c, progress) : null;
            Path jar = launcher ? Updates.downloadLauncher(c, progress) : null;
            return new Object[]{sync, jar};
        }, res -> {
            stopProgress();
            NexusClientStore.SyncResult sync = (NexusClientStore.SyncResult) res[0];
            if (sync != null) reportClient(c, sync);
            launcherJar = (Path) res[1];
            if (launcherJar != null) {
                readyToRestart(c.launcherVersion());
                Modal.confirm(I18n.t("update.restartTitle"), I18n.t("update.restartText", c.launcherVersion()),
                        I18n.t("update.restart"), false, this::restart);
            } else if (c.launcherUpdate()) {
                manualLauncher(c);
            } else {
                hideBanner();
            }
        }, err -> {
            stopProgress();
            Log.warn("Update failed: " + err);
            Toasts.error(I18n.t("update.failed", Fx.errorText(err)));
            offer(c);
        });
    }

    private void reportClient(Updates.Check c, NexusClientStore.SyncResult sync) {
        if (!sync.updated().isEmpty()) {
            Toasts.success(I18n.t("update.clientDone", c.clientVersion(), String.join(", ", sync.updated())));
        } else {
            Toasts.success(I18n.t("update.clientDownloaded", c.clientVersion()));
        }
        if (!sync.skipped().isEmpty()) Toasts.info(I18n.t("update.clientSkipped", String.join(", ", sync.skipped())));
        window.instancesChanged();
    }

    private void restart() {
        if (launcherJar == null) return;
        int running = GameProcess.runningCount();
        if (running > 0) {
            Toasts.info(I18n.t("update.closeGames", running));
            return;
        }
        try {
            Updates.startReplacement(launcherJar);
            window.exitForUpdate();
        } catch (Exception e) {
            Log.error("Cannot start the update", e);
            Toasts.error(I18n.t("update.failed", Fx.errorText(e)));
        }
    }

    private void openPage() {
        String page = check == null ? null : check.manifest().releasePage();
        if (page != null) window.openLink(page);
        else Toasts.info(I18n.t("update.noPage"));
    }

    private void showNotes() {
        if (check == null) return;
        StringBuilder sb = new StringBuilder();
        if (check.launcherUpdate()) {
            sb.append("NEXUS Launcher ").append(check.launcherVersion()).append("\n");
            String log = check.manifest().launcher().changelog();
            sb.append(log == null || log.isBlank() ? I18n.t("update.noNotes") : log.strip()).append("\n\n");
        }
        if (check.clientUpdate()) {
            sb.append("NEXUS Client ").append(check.clientVersion()).append("\n");
            String log = check.manifest().client().changelog();
            sb.append(log == null || log.isBlank() ? I18n.t("update.noNotes") : log.strip()).append("\n");
        }
        TextArea area = new TextArea(sb.toString().strip());
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(14);
        Modal m = Modal.of(I18n.t("update.notesTitle")).width(560).content(area);
        m.closeButton(I18n.t("common.close"), null, null);
        if (!isWorking() && launcherJar == null) {
            m.button(action.getText(), "primary", () -> {
                m.close();
                action.fire();
            });
        }
        m.show();
    }

    // ---- helpers ----

    private boolean isWorking() {
        return poll != null;
    }

    private void stopProgress() {
        if (poll != null) poll.stop();
        poll = null;
        bar.setVisible(false);
        bar.setManaged(false);
    }

    private void showBanner() {
        setVisible(true);
        setManaged(true);
    }

    private void hideBanner() {
        setVisible(false);
        setManaged(false);
    }
}
