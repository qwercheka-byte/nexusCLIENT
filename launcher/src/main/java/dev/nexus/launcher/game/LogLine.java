package dev.nexus.launcher.game;

/** One line of game output. */
public record LogLine(Level level, String text) {
    public enum Level { INFO, WARN, ERROR, DEBUG, LAUNCHER }

    public static Level guess(String line) {
        if (line.contains("/ERROR]") || line.contains("/FATAL]") || line.contains("[ERROR]") || line.startsWith("Exception")
                || line.startsWith("\tat ") || line.startsWith("Caused by:")) return Level.ERROR;
        if (line.contains("/WARN]") || line.contains("[WARN]") || line.contains("WARNING")) return Level.WARN;
        if (line.contains("/DEBUG]")) return Level.DEBUG;
        return Level.INFO;
    }
}
