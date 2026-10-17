package dev.nexus.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.FileUtil;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.net.Downloader;
import dev.nexus.launcher.net.Http;
import dev.nexus.launcher.net.Progress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java selection. By default the runtime Mojang publishes for the version (java-runtime-delta = Java 21,
 * java-runtime-gamma = Java 17, jre-legacy = Java 8, ...) is downloaded into runtimes/. A path set in the
 * instance or in the launcher settings overrides it.
 */
public final class JavaRuntimes {
    public static final String INDEX_URL =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final String MARKER = ".nexus-runtime";

    /** Java to use and its major version (-1 when unknown). */
    public record JavaInstall(Path executable, int major, String source) {}

    private JavaRuntimes() {}

    public static JavaInstall select(ResolvedVersion v, String instanceOverride, Progress progress) throws IOException {
        int required = v.javaVersion() != null ? v.javaVersion().majorVersion : 8;
        String custom = instanceOverride != null && !instanceOverride.isBlank() ? instanceOverride : Settings.get().javaPath;
        if (custom != null && !custom.isBlank()) {
            Path exe = Path.of(custom.trim());
            if (Files.isDirectory(exe)) exe = executableIn(exe);
            if (!Files.isRegularFile(exe)) throw new IOException(I18n.t("error.java.custom", custom));
            return new JavaInstall(exe, probeMajor(exe), "custom");
        }
        String component = v.javaVersion() != null && v.javaVersion().component != null ? v.javaVersion().component : "jre-legacy";
        String platform = OS.runtimePlatform();
        if (platform != null) {
            Path exe = ensureMojangRuntime(component, platform, progress);
            if (exe != null) return new JavaInstall(exe, required, "mojang:" + component);
            // Apple Silicon has no jre-legacy build: the x64 one runs through Rosetta 2.
            if (platform.equals("mac-os-arm64")) {
                exe = ensureMojangRuntime(component, "mac-os", progress);
                if (exe != null) return new JavaInstall(exe, required, "mojang:" + component + " (x64)");
            }
        }
        // No Mojang runtime for this platform: fall back to the system Java.
        Path system = systemJava();
        if (system == null) throw new IOException(I18n.t("error.java.none", required));
        int major = probeMajor(system);
        if (major > 0 && major < required) throw new IOException(I18n.t("error.java.old", major, required));
        return new JavaInstall(system, major, "system");
    }

    /** Installs (or verifies) a Mojang runtime component; returns null when it is not offered for the platform. */
    private static Path ensureMojangRuntime(String component, String platform, Progress progress) throws IOException {
        Path dir = LauncherDirs.runtimes().resolve(component).resolve(platform);
        Path marker = dir.resolve(MARKER);
        Path exe = executableIn(dir);

        JsonObject index;
        try {
            index = Http.getJson(INDEX_URL).getAsJsonObject();
        } catch (IOException e) {
            if (Files.isRegularFile(exe)) {
                Log.warn("Java runtime index unavailable, using installed " + component + ": " + e.getMessage());
                return exe;
            }
            throw e;
        }
        JsonObject plat = index.getAsJsonObject(platform);
        if (plat == null || !plat.has(component)) return null;
        JsonArray variants = plat.getAsJsonArray(component);
        if (variants.isEmpty()) return null;
        JsonObject manifestRef = variants.get(0).getAsJsonObject().getAsJsonObject("manifest");
        String manifestSha1 = manifestRef.get("sha1").getAsString();
        if (Files.isRegularFile(exe) && Files.isRegularFile(marker)
                && Files.readString(marker, StandardCharsets.UTF_8).trim().equals(manifestSha1)) {
            return exe;
        }

        progress.stage(I18n.t("stage.java", component));
        JsonObject manifest = Http.getJson(manifestRef.get("url").getAsString()).getAsJsonObject();
        JsonObject files = manifest.getAsJsonObject("files");
        List<Downloader.Task> tasks = new ArrayList<>();
        List<String[]> links = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : files.entrySet()) {
            Path target = dir.resolve(e.getKey()).normalize();
            if (!target.startsWith(dir)) continue;
            JsonObject f = e.getValue().getAsJsonObject();
            String type = f.get("type").getAsString();
            switch (type) {
                case "directory" -> Files.createDirectories(target);
                case "file" -> {
                    JsonObject raw = f.getAsJsonObject("downloads").getAsJsonObject("raw");
                    boolean exec = f.has("executable") && f.get("executable").getAsBoolean();
                    tasks.add(new Downloader.Task(raw.get("url").getAsString(), target, raw.get("sha1").getAsString(),
                            raw.get("size").getAsLong(), true, exec));
                }
                case "link" -> links.add(new String[]{e.getKey(), f.get("target").getAsString()});
                default -> Log.warn("Unknown runtime entry type " + type + " for " + e.getKey());
            }
        }
        Downloader.run(tasks, Settings.get().maxDownloads, progress);
        for (Downloader.Task t : tasks) if (t.executable()) FileUtil.makeExecutable(t.target());
        if (!OS.isWindows()) {
            for (String[] l : links) {
                Path link = dir.resolve(l[0]);
                Files.createDirectories(link.getParent());
                Files.deleteIfExists(link);
                try {
                    Files.createSymbolicLink(link, Path.of(l[1]));
                } catch (UnsupportedOperationException | IOException ex) {
                    Log.warn("Cannot create link " + link + ": " + ex.getMessage());
                }
            }
        }
        if (!Files.isRegularFile(exe)) throw new IOException("Java executable missing after install: " + exe);
        Files.writeString(marker, manifestSha1, StandardCharsets.UTF_8);
        Log.info("Installed Java runtime " + component + " for " + platform);
        return exe;
    }

    /** java executable inside a JDK/JRE folder (handles the macOS bundle layout). */
    public static Path executableIn(Path home) {
        String name = OS.isWindows() ? "javaw.exe" : "java";
        Path bundle = home.resolve("jre.bundle").resolve("Contents").resolve("Home").resolve("bin").resolve(name);
        if (OS.isMac() && Files.exists(bundle)) return bundle;
        Path direct = home.resolve("bin").resolve(name);
        if (OS.isMac() && !Files.exists(direct)) return bundle;
        return direct;
    }

    private static Path systemJava() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path exe = executableIn(Path.of(javaHome));
            if (Files.isRegularFile(exe)) return exe;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                Path exe = Path.of(dir).resolve(OS.isWindows() ? "javaw.exe" : "java");
                if (Files.isRegularFile(exe)) return exe;
            }
        }
        return null;
    }

    private static final Pattern VERSION = Pattern.compile("version \"(1\\.)?(\\d+)");

    /** Runs "java -version" and extracts the major version (-1 on failure). */
    public static int probeMajor(Path exe) {
        Path java = exe;
        if (OS.isWindows() && exe.getFileName().toString().equalsIgnoreCase("javaw.exe")) {
            Path alt = exe.resolveSibling("java.exe");
            if (Files.exists(alt)) java = alt;
        }
        try {
            Process p = new ProcessBuilder(java.toString(), "-version").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(10, TimeUnit.SECONDS);
            Matcher m = VERSION.matcher(out);
            if (m.find()) return Integer.parseInt(m.group(2));
        } catch (IOException e) {
            Log.warn("Cannot run " + java + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return -1;
    }
}
