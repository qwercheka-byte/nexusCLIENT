package dev.nexus.launcher.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Minimal launcher log: logs/launcher.log (previous run kept as launcher.prev.log) plus stderr. */
public final class Log {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static BufferedWriter out;

    private Log() {}

    public static synchronized void init(Path logsDir) {
        try {
            Path file = logsDir.resolve("launcher.log");
            if (Files.exists(file)) Files.move(file, logsDir.resolve("launcher.prev.log"), StandardCopyOption.REPLACE_EXISTING);
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[NEXUS] Cannot open launcher log: " + e);
        }
    }

    public static void info(String msg) { write("INFO", msg, null); }

    public static void warn(String msg) { write("WARN", msg, null); }

    public static void warn(String msg, Throwable t) { write("WARN", msg, t); }

    public static void error(String msg, Throwable t) { write("ERROR", msg, t); }

    private static synchronized void write(String level, String msg, Throwable t) {
        String line = "[" + LocalTime.now().format(TIME) + "] [" + Thread.currentThread().getName() + "/" + level + "] " + msg;
        String trace = "";
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            trace = sw.toString();
        }
        System.err.println(line);
        if (!trace.isEmpty()) System.err.print(trace);
        if (out != null) {
            try {
                out.write(line);
                out.newLine();
                if (!trace.isEmpty()) out.write(trace);
                out.flush();
            } catch (IOException ignored) {
                // logging must never break the launcher
            }
        }
    }
}
