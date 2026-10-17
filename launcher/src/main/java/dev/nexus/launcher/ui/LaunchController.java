package dev.nexus.launcher.ui;

import dev.nexus.launcher.auth.Account;
import dev.nexus.launcher.auth.AccountStore;
import dev.nexus.launcher.auth.MicrosoftAuth;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.game.GameLauncher;
import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.game.LogLine;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.net.Progress;
import javafx.application.Platform;

import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Starts games from the UI. Only one instance is prepared (downloaded/installed) at a time; any number of
 * prepared games may run at once. UI parts subscribe with {@link #addListener(Runnable)} and are notified on
 * the FX thread whenever preparation starts/ends or a game starts/exits.
 */
public final class LaunchController {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Instance preparing;
    private static volatile Progress progress;
    /** Window position before it was hidden for a game (some window managers forget it). */
    private static double[] hiddenAt;
    /** Lines produced while preparing (installer output etc.), shown in the console of that instance. */
    private static final List<LogLine> PREPARE_LOG = new CopyOnWriteArrayList<>();
    private static final java.util.Set<String> STOP_REQUESTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private LaunchController() {}

    public static void addListener(Runnable r) { LISTENERS.add(r); }

    public static void removeListener(Runnable r) { LISTENERS.remove(r); }

    private static void fire() {
        if (Platform.isFxApplicationThread()) LISTENERS.forEach(Runnable::run);
        else Platform.runLater(() -> LISTENERS.forEach(Runnable::run));
    }

    public static Instance preparing() { return preparing; }

    public static Progress progress() { return progress; }

    public static boolean isPreparing(Instance inst) {
        Instance p = preparing;
        return p != null && inst != null && p.id.equals(inst.id);
    }

    public static List<LogLine> prepareLog(Instance inst) {
        return isPreparing(inst) ? List.copyOf(PREPARE_LOG) : List.of();
    }

    public static void cancel() {
        Progress p = progress;
        if (p != null) p.cancel();
    }

    public static void stop(Instance inst) {
        GameProcess gp = GameProcess.running(inst);
        if (gp != null) {
            STOP_REQUESTED.add(inst.id);
            gp.kill();
        }
    }

    public static void launch(Instance inst) {
        if (inst == null) return;
        if (GameProcess.running(inst) != null) {
            Toasts.info(I18n.t("launch.alreadyRunning", inst.name));
            return;
        }
        if (preparing != null) {
            Toasts.info(I18n.t("launch.busy", preparing.name));
            return;
        }
        Account account = AccountStore.active();
        if (account == null) {
            Toasts.error(I18n.t("launch.noAccount"));
            MainWindow.get().showAccounts();
            return;
        }
        Settings.LaunchBehavior behavior = Settings.get().launchBehavior;
        Progress pr = new Progress();
        progress = pr;
        preparing = inst;
        PREPARE_LOG.clear();
        fire();

        Thread worker = new Thread(() -> {
            try {
                pr.bindWorker(Thread.currentThread());
                if (account.isMicrosoft()) {
                    pr.stage(I18n.t("stage.auth"));
                    if (MicrosoftAuth.ensureFresh(account)) AccountStore.save();
                }
                InstanceManager.save(inst);
                GameLauncher.LaunchSpec spec = GameLauncher.prepare(inst, account, pr,
                        line -> PREPARE_LOG.add(new LogLine(LogLine.Level.LAUNCHER, line)));
                pr.checkCancelled();
                boolean detached = behavior == Settings.LaunchBehavior.CLOSE;
                GameProcess gp = GameProcess.start(inst, spec, detached);
                if (!detached) {
                    gp.addListener(new GameProcess.Listener() {
                        @Override
                        public void onLine(LogLine line) { }

                        @Override
                        public void onExit(int exitCode, Path crashReport) {
                            gp.removeListener(this);
                            Platform.runLater(() -> onGameExit(inst, exitCode, crashReport));
                        }
                    }, lines -> { });
                }
                Platform.runLater(() -> onGameStarted(inst, behavior));
            } catch (InterruptedIOException e) {
                Platform.runLater(() -> Toasts.info(I18n.t("launch.cancelled")));
            } catch (Throwable t) {
                Log.error("Launch of " + inst.name + " failed", t);
                Platform.runLater(() -> Modal.message(I18n.t("launch.failed.title"), I18n.t("launch.failed.text", Fx.errorText(t))));
            } finally {
                preparing = null;
                progress = null;
                fire();
            }
        }, "nexus-launch");
        worker.setDaemon(true);
        worker.start();
    }

    private static void onGameStarted(Instance inst, Settings.LaunchBehavior behavior) {
        Log.info("Game started: " + inst.name);
        switch (behavior) {
            case CLOSE -> {
                MainWindow.get().saveWindowState();
                Platform.exit();
                System.exit(0);
            }
            case HIDE -> {
                var st = MainWindow.get().stage();
                hiddenAt = new double[]{st.getX(), st.getY()};
                st.hide();
            }
            case KEEP -> {
                if (Settings.get().openConsoleOnLaunch) MainWindow.get().showInstance(inst, "console");
            }
        }
        fire();
    }

    private static void onGameExit(Instance inst, int code, Path crashReport) {
        var stage = MainWindow.get().stage();
        boolean wasHidden = !stage.isShowing();
        if (wasHidden && GameProcess.runningCount() == 0) {
            stage.show();
            if (hiddenAt != null) {
                stage.setX(hiddenAt[0]);
                stage.setY(hiddenAt[1]);
            }
            stage.toFront();
            stage.requestFocus();
        }
        boolean stoppedByUser = STOP_REQUESTED.remove(inst.id);
        if (code != 0 && !stoppedByUser) {
            Modal m = Modal.of(I18n.t("crash.title"))
                    .text(I18n.t(crashReport != null ? "crash.textReport" : "crash.text", inst.name, code));
            if (crashReport != null) m.button(I18n.t("crash.openReport"), null, () -> Fx.openPath(crashReport));
            m.closeButton(I18n.t("crash.openConsole"), null, () -> MainWindow.get().showInstance(inst, "console"));
            m.closeButton(I18n.t("common.close"), "primary", null);
            m.show();
        }
        fire();
    }
}
