package dev.nexus.launcher.game;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * With Mojang's logging configuration the game prints log4j XML events to stdout. This turns them back into
 * readable lines; any other output passes through unchanged.
 */
public final class Log4jXmlParser {
    private static final Pattern ATTR = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern MESSAGE = Pattern.compile("<log4j:Message><!\\[CDATA\\[(.*?)]]></log4j:Message>", Pattern.DOTALL);
    private static final Pattern THROWABLE = Pattern.compile("<log4j:Throwable><!\\[CDATA\\[(.*?)]]></log4j:Throwable>", Pattern.DOTALL);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StringBuilder event = new StringBuilder();
    private boolean inEvent;

    /** Feeds one raw line; returns zero or more parsed lines. */
    public List<LogLine> feed(String raw) {
        List<LogLine> out = new ArrayList<>(1);
        if (!inEvent && raw.trim().startsWith("<log4j:Event")) {
            inEvent = true;
            event.setLength(0);
        }
        if (!inEvent) {
            out.add(new LogLine(LogLine.guess(raw), raw));
            return out;
        }
        event.append(raw).append('\n');
        if (raw.contains("</log4j:Event>")) {
            inEvent = false;
            parse(event.toString(), out);
            event.setLength(0);
        } else if (event.length() > 1_000_000) {
            // Not a well-formed event after all: flush it as text
            inEvent = false;
            for (String l : event.toString().split("\n")) out.add(new LogLine(LogLine.guess(l), l));
            event.setLength(0);
        }
        return out;
    }

    private static void parse(String xml, List<LogLine> out) {
        int headerEnd = xml.indexOf('>');
        String header = headerEnd > 0 ? xml.substring(0, headerEnd) : xml;
        String level = "INFO", thread = "?", logger = "", time = "";
        Matcher a = ATTR.matcher(header);
        while (a.find()) {
            switch (a.group(1)) {
                case "level" -> level = a.group(2);
                case "thread" -> thread = a.group(2);
                case "logger" -> logger = a.group(2);
                case "timestamp" -> {
                    try {
                        time = TIME.format(Instant.ofEpochMilli(Long.parseLong(a.group(2))));
                    } catch (NumberFormatException ignored) {
                        // leave empty
                    }
                }
                default -> { }
            }
        }
        LogLine.Level lvl = switch (level) {
            case "WARN" -> LogLine.Level.WARN;
            case "ERROR", "FATAL" -> LogLine.Level.ERROR;
            case "DEBUG", "TRACE" -> LogLine.Level.DEBUG;
            default -> LogLine.Level.INFO;
        };
        Matcher m = MESSAGE.matcher(xml);
        String msg = m.find() ? m.group(1) : "";
        String prefix = "[" + time + "] [" + thread + "/" + level + "]" + (logger.isEmpty() ? "" : " (" + shortLogger(logger) + ")") + ": ";
        String[] lines = msg.split("\n", -1);
        out.add(new LogLine(lvl, prefix + lines[0]));
        for (int i = 1; i < lines.length; i++) if (!lines[i].isEmpty()) out.add(new LogLine(lvl, lines[i]));
        Matcher t = THROWABLE.matcher(xml);
        if (t.find()) for (String l : t.group(1).split("\n")) if (!l.isBlank()) out.add(new LogLine(LogLine.Level.ERROR, l));
    }

    private static String shortLogger(String logger) {
        int dot = logger.lastIndexOf('.');
        return dot >= 0 ? logger.substring(dot + 1) : logger;
    }
}
