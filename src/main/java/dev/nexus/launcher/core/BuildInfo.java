package dev.nexus.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    public static final String NAME = "NEXUS Launcher";
    public static final String VERSION;
    public static final String JAVAFX;
    /** Version of the NEXUS Client jars bundled in {@code /bundled}. */
    public static final String NEXUS_CLIENT;
    /** GitHub repository (owner/name) whose latest release carries nexus-update.json, or "". */
    public static final String UPDATE_REPO;
    /** Full address of nexus-update.json, or "". Wins over {@link #UPDATE_REPO}. */
    public static final String UPDATE_URL;

    static {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/dev/nexus/launcher/build.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // defaults below
        }
        VERSION = clean(p.getProperty("version"), "dev");
        JAVAFX = clean(p.getProperty("javafx"), System.getProperty("javafx.runtime.version", "?"));
        NEXUS_CLIENT = clean(p.getProperty("nexusClient"), "1.0.0");
        UPDATE_REPO = clean(p.getProperty("updateRepo"), "").trim();
        UPDATE_URL = clean(p.getProperty("updateUrl"), "").trim();
    }

    private BuildInfo() {}

    private static String clean(String v, String def) {
        return v == null || v.isBlank() || v.startsWith("${") ? def : v;
    }

    /** User agent sent with every request (Modrinth asks clients to identify themselves). */
    public static String userAgent() {
        return "NexusLauncher/" + VERSION + " (dev.nexus.launcher)";
    }
}
