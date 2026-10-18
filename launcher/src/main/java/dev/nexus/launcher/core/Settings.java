package dev.nexus.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/** Launcher-wide settings stored in settings.json. Unknown or invalid values are replaced by defaults. */
public final class Settings {
    public static final Set<String> LANGUAGES = Set.of("ru", "en");
    public static final Set<String> ACCENTS = Set.of("violet", "cyan", "emerald", "rose", "amber");
    public static final String DEFAULT_JVM_ARGS =
            "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M";

    public enum LaunchBehavior { KEEP, HIDE, CLOSE }

    public int schema = 1;
    public String language = Locale.getDefault().getLanguage().equals("ru") ? "ru" : "en";
    public String accent = "violet";
    public int maxDownloads = 8;
    public int minMemoryMb = 512;
    public int maxMemoryMb = recommendedMaxMemory();
    public String jvmArgs = DEFAULT_JVM_ARGS;
    /** Empty = choose automatically (Mojang runtime for the version). */
    public String javaPath = "";
    public LaunchBehavior launchBehavior = LaunchBehavior.HIDE;
    public boolean openConsoleOnLaunch = false;
    public boolean showSnapshots = false;
    public boolean showOldVersions = false;
    public String microsoftClientId = "";
    public String activeAccount = "";
    public String selectedInstance = "";
    public double windowWidth = 1180;
    public double windowHeight = 740;
    public boolean windowMaximized = false;
    /** Themed title bar instead of the system window frame (applied on the next start). */
    public boolean customTitleBar = true;
    /** Check for launcher / NEXUS Client updates on start-up. */
    public boolean checkUpdates = true;
    /** Animated photo background of the window (photos from <data>/backgrounds or the bundled ones). */
    public boolean photoBackground = true;
    public double photoDim = 0.55;
    public boolean photoShuffle = true;
    /** Address of nexus-update.json entered by the user; empty = the address built into the launcher. */
    public String updateUrl = "";
    /** Cloud AI assistant: provider id ("deepseek"/"groq"/"openrouter"), the player's own API key (stored locally only),
        consent to send build context (mods + log tail) to the provider, and the model name ("" = default). */
    public String aiProvider = "deepseek";
    public String aiKey = "";
    public boolean aiConsent = false;
    public String aiModel = "";
    /** Launcher version of the previous run, used to show "updated to X" once. */
    public String lastVersion = "";

    private static Settings current;

    public static Settings get() {
        return current;
    }

    public static int recommendedMaxMemory() {
        long total = OS.totalMemoryMb();
        if (total <= 0) return 2048;
        long half = total / 2;
        if (half >= 4096) return 4096;
        if (half >= 3072) return 3072;
        return half >= 2048 ? 2048 : 1024;
    }

    public static void load() {
        Path file = LauncherDirs.settingsFile();
        Settings s = null;
        if (Files.exists(file)) {
            try {
                s = Json.read(file, Settings.class);
            } catch (IOException e) {
                Log.warn("settings.json is corrupted, keeping a backup and using defaults", e);
                try {
                    Files.move(file, file.resolveSibling("settings.corrupt-" + System.currentTimeMillis() + ".json"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
        current = s == null ? new Settings() : s.validated();
    }

    public static void save() {
        Settings snapshot = current;
        Tasks.io().execute(() -> {
            synchronized (Settings.class) {
                try {
                    Json.write(LauncherDirs.settingsFile(), snapshot);
                } catch (IOException e) {
                    Log.error("Cannot save settings", e);
                }
            }
        });
    }

    /** Writes settings.json synchronously (used right before the JVM exits). */
    public static void saveNow() {
        synchronized (Settings.class) {
            try {
                Json.write(LauncherDirs.settingsFile(), current);
            } catch (IOException e) {
                Log.error("Cannot save settings", e);
            }
        }
    }

    private Settings validated() {
        Settings d = new Settings();
        if (language == null || !LANGUAGES.contains(language)) language = d.language;
        if (accent == null || !ACCENTS.contains(accent)) accent = d.accent;
        maxDownloads = clamp(maxDownloads, 1, 32);
        maxMemoryMb = clamp(maxMemoryMb, 512, 65536);
        minMemoryMb = clamp(minMemoryMb, 256, maxMemoryMb);
        if (jvmArgs == null) jvmArgs = d.jvmArgs;
        if (javaPath == null) javaPath = "";
        if (launchBehavior == null) launchBehavior = d.launchBehavior;
        if (microsoftClientId == null) microsoftClientId = "";
        if (activeAccount == null) activeAccount = "";
        if (selectedInstance == null) selectedInstance = "";
        if (updateUrl == null) updateUrl = "";
        if (aiProvider == null) aiProvider = d.aiProvider;
        if (aiKey == null) aiKey = "";
        if (aiModel == null) aiModel = "";
        if (lastVersion == null) lastVersion = "";
        if (!(windowWidth >= 900) || windowWidth > 10000) windowWidth = d.windowWidth;
        if (!(windowHeight >= 600) || windowHeight > 10000) windowHeight = d.windowHeight;
        if (!(photoDim >= 0) || photoDim > 0.9) photoDim = d.photoDim;
        schema = 1;
        return this;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
