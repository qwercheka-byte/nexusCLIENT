package dev.nexus.launcher.update;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Small separate process that finishes a launcher self-update. It runs from a temporary copy of its own class
 * files, so nothing inside the program's {@code app} folder is in use while the folders are swapped.
 *
 * <pre>
 *   javaw -cp &lt;helper dir&gt; dev.nexus.launcher.update.UpdateHelper &lt;pid&gt; &lt;app dir&gt; &lt;new app dir&gt; &lt;java&gt; [jvm args…]
 * </pre>
 *
 * Steps: wait until the launcher process {@code pid} has exited, rename {@code app} to {@code app.old}, rename
 * {@code app.new} to {@code app} (retrying while Windows still holds a file), start the launcher again
 * ({@code java -cp app dev.nexus.launcher.Main}, exactly what "Nexus Launcher.exe" runs). When the swap fails the
 * old folder is put back and the old version is started, so the user never ends up without a launcher.
 * Uses only java.base: no Gson, no JavaFX, no other launcher classes.
 */
public final class UpdateHelper {
    private static final String MAIN_CLASS = "dev.nexus.launcher.Main";

    private UpdateHelper() {}

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("usage: UpdateHelper <pid> <app dir> <new app dir> <java> [jvm args...]");
            System.exit(2);
        }
        long pid = Long.parseLong(args[0]);
        Path app = Path.of(args[1]).toAbsolutePath();
        Path fresh = Path.of(args[2]).toAbsolutePath();
        String java = args[3];
        List<String> jvmArgs = Arrays.asList(args).subList(4, args.length);

        log("waiting for launcher process " + pid);
        ProcessHandle.of(pid).ifPresent(p -> {
            try {
                p.onExit().get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                log("launcher did not exit in time: " + e);
            }
        });
        sleep(400);

        boolean replaced = swap(app, fresh);
        log(replaced ? "update installed" : "update failed, starting the previous version");
        relaunch(java, jvmArgs, app);
    }

    static boolean swap(Path app, Path fresh) {
        if (!Files.isRegularFile(fresh.resolve("dev/nexus/launcher/Main.class"))) {
            log("new version not found: " + fresh);
            return false;
        }
        Path old = app.resolveSibling(app.getFileName() + ".old");
        try {
            deleteRecursively(old);
        } catch (IOException e) {
            log("cannot remove a stale " + old + ": " + e);
            return false;
        }
        // Windows may keep files locked for a moment after the process exits (antivirus, indexer).
        if (!retryMove(app, old)) return false;
        if (retryMove(fresh, app)) {
            try {
                deleteRecursively(old);
            } catch (IOException e) {
                log("previous version left in " + old + " (removed on next start): " + e);
            }
            return true;
        }
        // roll back
        if (!retryMove(old, app)) log("CANNOT RESTORE " + app + " from " + old);
        return false;
    }

    private static boolean retryMove(Path from, Path to) {
        IOException last = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                Files.move(from, to);
                return true;
            } catch (IOException e) {
                last = e;
                sleep(500);
            }
        }
        log("cannot move " + from + " -> " + to + ": " + last);
        return false;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void relaunch(String java, List<String> jvmArgs, Path app) {
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.addAll(jvmArgs);
        cmd.add("-cp");
        cmd.add(app.toString());
        cmd.add(MAIN_CLASS);
        try {
            Path dir = app.getParent();
            new ProcessBuilder(cmd)
                    .directory(dir == null ? null : dir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            log("started " + String.join(" ", cmd));
        } catch (IOException e) {
            log("cannot start the launcher: " + e);
        }
    }

    private static void log(String msg) {
        System.out.println("[" + LocalTime.now().withNano(0) + "] " + msg);
        System.out.flush();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
