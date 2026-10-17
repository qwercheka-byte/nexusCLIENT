package dev.nexus.launcher.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Small separate process that finishes a launcher self-update. It runs from a temporary copy of the old
 * launcher jar, so neither the old nor the new jar is locked by it (Windows does not allow replacing a jar
 * that a running JVM has open).
 *
 * <pre>
 *   java -cp updater.jar dev.nexus.launcher.update.UpdateHelper &lt;pid&gt; &lt;target jar&gt; &lt;new jar&gt; &lt;java&gt; [jvm args…]
 * </pre>
 *
 * Steps: wait until the launcher process {@code pid} has exited, back up the target as {@code *.old}, move the
 * new jar over it (retrying while the file is still locked), start the launcher again. When the replacement
 * fails the backup is restored and the old version is started, so the user never ends up without a launcher.
 * Uses only java.base: no Gson, no JavaFX, no other launcher classes.
 */
public final class UpdateHelper {
    private UpdateHelper() {}

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("usage: UpdateHelper <pid> <target jar> <new jar> <java> [jvm args...]");
            System.exit(2);
        }
        long pid = Long.parseLong(args[0]);
        Path target = Path.of(args[1]);
        Path source = Path.of(args[2]);
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

        boolean replaced = replace(target, source);
        log(replaced ? "update installed" : "update failed, starting the previous version");
        relaunch(java, jvmArgs, target);
    }

    static boolean replace(Path target, Path source) {
        if (!Files.isRegularFile(source)) {
            log("new jar not found: " + source);
            return false;
        }
        Path backup = target.resolveSibling(target.getFileName() + ".old");
        try {
            if (Files.exists(target)) Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log("cannot back up " + target + ": " + e);
            return false;
        }
        IOException last = null;
        // Windows may keep the file locked for a moment after the process exits (antivirus, indexer).
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException ignored) {
                    // a stale backup is harmless
                }
                return true;
            } catch (IOException e) {
                last = e;
                sleep(500);
            }
        }
        log("cannot replace " + target + ": " + last);
        try {
            if (Files.exists(backup)) Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log("cannot restore the backup: " + e);
        }
        return false;
    }

    private static void relaunch(String java, List<String> jvmArgs, Path target) {
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.addAll(jvmArgs);
        cmd.add("-jar");
        cmd.add(target.toString());
        try {
            Path dir = target.toAbsolutePath().getParent();
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
