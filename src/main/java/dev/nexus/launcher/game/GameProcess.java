package dev.nexus.launcher.game;

import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Tasks;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** A running game. Keeps the last lines of output in memory and the whole output in a file. */
public final class GameProcess {
    public static final int BUFFER_LINES = 6000;
    private static final Map<String, GameProcess> RUNNING = new ConcurrentHashMap<>();

    public interface Listener {
        void onLine(LogLine line);

        /** @param crashReport newest crash report written during this session, or null */
        void onExit(int exitCode, Path crashReport);
    }

    private final Instance instance;
    private final Process process;
    private final long startedAt = System.currentTimeMillis();
    private final ArrayDeque<LogLine> buffer = new ArrayDeque<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Path outputFile;
    private volatile boolean finished;

    private GameProcess(Instance instance, Process process, Path outputFile) {
        this.instance = instance;
        this.process = process;
        this.outputFile = outputFile;
    }

    public static GameProcess running(Instance inst) {
        GameProcess p = RUNNING.get(inst.id);
        return p != null && !p.finished ? p : null;
    }

    public static int runningCount() {
        return (int) RUNNING.values().stream().filter(p -> !p.finished).count();
    }

    /**
     * Starts the game.
     *
     * @param detached the launcher is going to exit: output goes straight to a file and is not followed
     */
    public static GameProcess start(Instance inst, GameLauncher.LaunchSpec spec, boolean detached) throws IOException {
        Path logs = inst.dir.resolve("logs");
        Files.createDirectories(logs);
        Path out = logs.resolve("launcher-output.log");
        ProcessBuilder pb = new ProcessBuilder(spec.command()).directory(spec.workingDir().toFile()).redirectErrorStream(true);
        if (detached) pb.redirectOutput(out.toFile());
        Log.info("Launching " + inst.name + ": " + redact(spec.command()));
        Process p = pb.start();
        GameProcess gp = new GameProcess(inst, p, out);
        RUNNING.put(inst.id, gp);
        if (!detached) {
            Thread reader = Tasks.factory("nexus-game-output").newThread(gp::pump);
            reader.start();
        }
        p.onExit().thenAccept(proc -> gp.finish(proc.exitValue()));
        return gp;
    }

    public Instance instance() { return instance; }

    public long startedAt() { return startedAt; }

    public Path outputFile() { return outputFile; }

    public boolean isAlive() { return !finished && process.isAlive(); }

    public void kill() {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        Tasks.io().execute(() -> {
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** Adds a listener and replays the buffered output to it (under the buffer lock, so no line is lost). */
    public void addListener(Listener l, Consumer<List<LogLine>> replay) {
        synchronized (buffer) {
            replay.accept(new ArrayList<>(buffer));
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void pump() {
        Charset cs = Charset.defaultCharset();
        Log4jXmlParser parser = new Log4jXmlParser();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), cs));
             BufferedWriter w = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = r.readLine()) != null) {
                for (LogLine line : parser.feed(raw)) {
                    w.write(line.text());
                    w.newLine();
                    synchronized (buffer) {
                        buffer.addLast(line);
                        if (buffer.size() > BUFFER_LINES) buffer.removeFirst();
                        for (Listener l : listeners) l.onLine(line);
                    }
                }
                w.flush();
            }
        } catch (IOException e) {
            Log.warn("Game output reader stopped: " + e.getMessage());
        }
    }

    private void finish(int code) {
        finished = true;
        RUNNING.remove(instance.id, this);
        long seconds = (System.currentTimeMillis() - startedAt) / 1000;
        instance.playtimeSeconds += seconds;
        instance.lastPlayed = startedAt;
        try {
            InstanceManager.save(instance);
        } catch (IOException e) {
            Log.warn("Cannot save play time of " + instance.id, e);
        }
        Path crash = code != 0 ? newestCrashReport() : null;
        Log.info("Game " + instance.name + " exited with code " + code + " after " + seconds + " s" + (crash != null ? ", crash report " + crash : ""));
        LogLine end = new LogLine(LogLine.Level.LAUNCHER, "-- exit code " + code + " --");
        synchronized (buffer) {
            buffer.addLast(end);
            for (Listener l : listeners) {
                l.onLine(end);
                l.onExit(code, crash);
            }
        }
    }

    private Path newestCrashReport() {
        Path dir = instance.gameDir().resolve("crash-reports");
        if (!Files.isDirectory(dir)) return null;
        Path best = null;
        long bestTime = startedAt - 1000;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : ds) {
                long t = Files.getLastModifiedTime(p).toMillis();
                if (t >= bestTime) {
                    bestTime = t;
                    best = p;
                }
            }
        } catch (IOException ignored) {
            // no report
        }
        return best;
    }

    /** Command line for the log with the access token hidden. */
    private static String redact(List<String> cmd) {
        List<String> copy = new ArrayList<>(cmd);
        for (int i = 0; i < copy.size() - 1; i++) {
            if (copy.get(i).equals("--accessToken")) copy.set(i + 1, "********");
        }
        String s = String.join(" ", copy);
        return s.length() > 4000 ? s.substring(0, 4000) + "…" : s;
    }
}
