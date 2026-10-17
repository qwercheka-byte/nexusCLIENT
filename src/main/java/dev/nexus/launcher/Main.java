package dev.nexus.launcher;

import dev.nexus.launcher.auth.Account;
import dev.nexus.launcher.auth.AccountStore;
import dev.nexus.launcher.auth.MicrosoftAuth;
import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.game.GameLauncher;
import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.game.LogLine;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.net.Progress;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point. Deliberately not a JavaFX {@code Application}: with JavaFX on the class path (self-contained
 * jar) the main class must not extend it.
 *
 * <pre>
 *   java -jar nexus-launcher.jar                    open the launcher
 *   java -jar nexus-launcher.jar --launch "My pack" start an instance without the UI (for desktop shortcuts)
 *   java -jar nexus-launcher.jar --list             list instances
 * </pre>
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        LauncherDirs.init();
        Log.init(LauncherDirs.logs());
        Settings.load();
        I18n.setLanguage(Settings.get().language);
        AccountStore.load();
        Log.info(BuildInfo.NAME + " " + BuildInfo.VERSION + " on " + System.getProperty("os.name") + " " + OS.ARCH
                + ", Java " + System.getProperty("java.version") + ", data " + LauncherDirs.root());

        if (args.length >= 1 && args[0].equals("--list")) {
            for (Instance i : InstanceManager.reload()) System.out.println(i.id + "\t" + i.name + "\t" + i.describe().replace(" \u00b7 ", " | "));
            return;
        }
        if (args.length >= 2 && args[0].equals("--launch")) {
            System.exit(launchHeadless(args[1]));
        }
        InstanceManager.reload();
        LauncherApp.start(args);
    }

    /** Prepares and starts an instance, printing progress and game output to the console. */
    private static int launchHeadless(String idOrName) throws Exception {
        InstanceManager.reload();
        Instance inst = InstanceManager.find(idOrName);
        if (inst == null) {
            for (Instance i : InstanceManager.all()) if (i.name.equalsIgnoreCase(idOrName)) inst = i;
        }
        if (inst == null) {
            System.err.println("Instance not found: " + idOrName);
            return 2;
        }
        Account account = AccountStore.active();
        if (account == null) {
            System.err.println(I18n.t("cli.noAccount"));
            return 3;
        }
        if (account.isMicrosoft() && MicrosoftAuth.ensureFresh(account)) AccountStore.save();

        Progress progress = new Progress();
        Thread reporter = new Thread(() -> {
            String last = "";
            while (!Thread.currentThread().isInterrupted()) {
                double f = progress.fraction();
                String line = progress.stage() + (f >= 0 ? String.format(" %3.0f%%", f * 100) : "");
                if (!line.equals(last)) {
                    System.out.println("[launcher] " + line);
                    last = line;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "progress");
        reporter.setDaemon(true);
        reporter.start();
        GameLauncher.LaunchSpec spec = GameLauncher.prepare(inst, account, progress, l -> System.out.println("[installer] " + l));
        reporter.interrupt();

        GameProcess game = GameProcess.start(inst, spec, false);
        CountDownLatch done = new CountDownLatch(1);
        int[] code = {0};
        game.addListener(new GameProcess.Listener() {
            @Override
            public void onLine(LogLine line) {
                System.out.println(line.text());
            }

            @Override
            public void onExit(int exitCode, Path crashReport) {
                code[0] = exitCode;
                if (crashReport != null) System.out.println("[launcher] Crash report: " + crashReport);
                done.countDown();
            }
        }, (List<LogLine> replay) -> replay.forEach(l -> System.out.println(l.text())));
        done.await();
        return code[0];
    }
}
