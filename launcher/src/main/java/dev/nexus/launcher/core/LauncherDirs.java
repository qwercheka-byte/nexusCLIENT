package dev.nexus.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Directory layout of the launcher. The shared part (versions, libraries, assets) uses the same structure as
 * the official {@code .minecraft} folder, which lets the official Forge/NeoForge installers work on it directly.
 *
 * <pre>
 * root/
 *   versions/  libraries/  assets/      shared game files
 *   runtimes/                           Java runtimes downloaded from Mojang
 *   instances/&lt;id&gt;/instance.json       one folder per instance, game files in instances/&lt;id&gt;/minecraft
 *   natives/  cache/  logs/
 *   settings.json  accounts.json
 * </pre>
 */
public final class LauncherDirs {
    private static Path root;

    private LauncherDirs() {}

    /** Resolves and creates the root directory. Called once at start-up. */
    public static void init() throws IOException {
        Path r;
        String override = System.getProperty("nexus.launcher.dir");
        if (override == null || override.isBlank()) override = System.getenv("NEXUS_LAUNCHER_DIR");
        if (override != null && !override.isBlank()) {
            r = Path.of(override);
        } else {
            String home = System.getProperty("user.home");
            r = switch (OS.TYPE) {
                case WINDOWS -> {
                    String appData = System.getenv("APPDATA");
                    yield Path.of(appData != null ? appData : home, ".nexus-launcher");
                }
                case MACOS -> Path.of(home, "Library", "Application Support", "NexusLauncher");
                default -> {
                    String xdg = System.getenv("XDG_DATA_HOME");
                    yield (xdg != null && !xdg.isBlank()) ? Path.of(xdg, "nexus-launcher") : Path.of(home, ".local", "share", "nexus-launcher");
                }
            };
        }
        root = r.toAbsolutePath().normalize();
        for (Path p : new Path[]{root, versions(), libraries(), assets(), runtimes(), instances(), natives(), cache(), logs()}) {
            Files.createDirectories(p);
        }
    }

    public static Path root() { return root; }
    public static Path versions() { return root.resolve("versions"); }
    public static Path libraries() { return root.resolve("libraries"); }
    public static Path assets() { return root.resolve("assets"); }
    public static Path runtimes() { return root.resolve("runtimes"); }
    public static Path instances() { return root.resolve("instances"); }
    public static Path natives() { return root.resolve("natives"); }
    public static Path cache() { return root.resolve("cache"); }
    public static Path logs() { return root.resolve("logs"); }
    public static Path settingsFile() { return root.resolve("settings.json"); }
    public static Path accountsFile() { return root.resolve("accounts.json"); }

    public static Path versionDir(String id) { return versions().resolve(id); }
    public static Path versionJson(String id) { return versionDir(id).resolve(id + ".json"); }
    public static Path versionJar(String id) { return versionDir(id).resolve(id + ".jar"); }
}
