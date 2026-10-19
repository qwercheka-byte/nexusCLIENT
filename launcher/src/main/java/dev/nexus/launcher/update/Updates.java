package dev.nexus.launcher.update;

import dev.nexus.launcher.Main;
import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.FileUtil;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.game.Loaders;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.net.Http;
import dev.nexus.launcher.net.Progress;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Update checks and installation for the launcher itself and for NEXUS Client.
 * Nothing is installed without the user's consent: {@link #check()} only reads the manifest, the UI asks,
 * and only then {@link #installClient} / {@link #downloadLauncher} run.
 */
public final class Updates {
    /** Name of the program folder with the launcher classes, next to "Nexus Launcher.exe" and runtime\\. */
    public static final String APP_DIR = "app";

    /** A NEXUS Client package to download for one Minecraft version. */
    public record ClientFile(String mc, UpdateManifest.FileRef file) {}

    /**
     * Result of a check. {@code launcherVersion} is null when the launcher is up to date, {@code launcherFile} is
     * null when the release has no package for this platform. {@code clientFiles} is empty when NEXUS Client is up to date.
     */
    public record Check(UpdateManifest manifest, String launcherVersion, UpdateManifest.FileRef launcherFile,
                        String clientVersion, List<ClientFile> clientFiles) {
        public boolean launcherUpdate() { return launcherVersion != null; }

        public boolean clientUpdate() { return !clientFiles.isEmpty(); }

        public boolean any() { return launcherUpdate() || clientUpdate(); }
    }

    private Updates() {}

    public static boolean configured() {
        return UpdateManifest.address() != null;
    }

    /** Downloads and evaluates nexus-update.json. */
    public static Check check() throws IOException {
        String url = UpdateManifest.address();
        if (url == null) throw new IOException("Update address is not configured");
        UpdateManifest m = UpdateManifest.parse(Http.getJson(url).getAsJsonObject(), url);

        String launcherVersion = null;
        UpdateManifest.FileRef launcherFile = null;
        if (m.launcher() != null && Loaders.compareVersions(m.launcher().version(), BuildInfo.VERSION) > 0 && !BuildInfo.VERSION.equals("dev")) {
            launcherVersion = m.launcher().version();
            launcherFile = m.launcher().files().get(platformKey());
        }
        List<ClientFile> clientFiles = new ArrayList<>();
        String clientVersion = null;
        if (m.client() != null) {
            clientVersion = m.client().version();
            for (Map.Entry<String, UpdateManifest.FileRef> e : m.client().files().entrySet()) {
                if (!NexusClientStore.has(e.getKey(), clientVersion)) clientFiles.add(new ClientFile(e.getKey(), e.getValue()));
            }
        }
        Log.info("Update check: launcher " + BuildInfo.VERSION + " -> " + (m.launcher() == null ? "-" : m.launcher().version())
                + ", NEXUS Client " + clientVersion + " (" + clientFiles.size() + " new file(s))");
        return new Check(m, launcherVersion, launcherFile, clientVersion, clientFiles);
    }

    // ---- NEXUS Client ----

    /** Downloads the new NEXUS Client packages and updates every instance that has NEXUS Client installed. */
    public static NexusClientStore.SyncResult installClient(Check c, Progress progress) throws IOException {
        long total = c.clientFiles().stream().mapToLong(f -> Math.max(0, f.file().size())).sum();
        progress.set(0);
        progress.total(total > 0 ? total : -1);
        Files.createDirectories(NexusClientStore.dir());
        for (ClientFile f : c.clientFiles()) {
            progress.checkCancelled();
            progress.detail("NEXUS Client " + c.clientVersion() + " (" + f.mc() + ")");
            // stored as a .zip package; it becomes a .jar only when installed into an instance's mods folder
            Path target = NexusClientStore.dir().resolve(NexusClientStore.Source.storeName(c.clientVersion(), f.mc()));
            download(f.file(), target, progress);
            NexusClientStore.pruneOlder(f.mc(), c.clientVersion());
        }
        return NexusClientStore.syncAll();
    }

    // ---- launcher ----

    /**
     * Manifest key of the launcher package for this platform. The package is a zip with the contents of the
     * {@code app} folder (classes and resources, no jar files). Only Windows builds are published.
     */
    public static String platformKey() {
        return switch (OS.TYPE) {
            case WINDOWS -> "win-app";
            case MACOS -> "mac-app";
            default -> "linux-app";
        };
    }

    /**
     * The {@code app} folder the launcher is running from ({@code <install>/app}, started by "Nexus Launcher.exe"
     * as {@code runtime\bin\javaw.exe -cp app dev.nexus.launcher.Main}), or null when it runs from somewhere else
     * (an IDE, {@code ./gradlew run}).
     */
    public static Path currentAppDir() {
        try {
            Path p = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            if (!Files.isDirectory(p) || !p.getFileName().toString().equals(APP_DIR)) return null;
            return Files.isRegularFile(p.resolve("dev/nexus/launcher/Main.class")) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Why the launcher cannot replace itself ("notInstalled", "readOnly"), or null when it can. */
    public static String selfUpdateBlocker() {
        Path app = currentAppDir();
        if (app == null) return "notInstalled";
        Path dir = app.getParent();
        try {
            Path probe = Files.createTempFile(dir, ".nexus-write-test", ".tmp");
            Files.delete(probe);
        } catch (IOException e) {
            return "readOnly";
        }
        return null;
    }

    /**
     * Downloads and verifies the launcher package and unpacks it next to the running program as {@code app.new}.
     * The running {@code app} folder is not touched; {@link #startReplacement} swaps the folders after exit.
     */
    public static Path downloadLauncher(Check c, Progress progress) throws IOException {
        if (c.launcherFile() == null) throw new IOException("The release has no launcher build for " + platformKey());
        Path app = currentAppDir();
        if (app == null) throw new IOException("The launcher is not running from an installed app folder");
        Path dir = updateDir();
        Files.createDirectories(dir);
        Path zip = dir.resolve("nexus-launcher-" + c.launcherVersion() + "-app.zip");
        progress.set(0);
        progress.total(c.launcherFile().size() > 0 ? c.launcherFile().size() : -1);
        progress.detail("NEXUS Launcher " + c.launcherVersion());
        download(c.launcherFile(), zip, progress);

        Path target = app.resolveSibling(APP_DIR + ".new");
        try {
            FileUtil.deleteRecursively(target);
            unzip(zip, target);
            if (!Files.isRegularFile(target.resolve("dev/nexus/launcher/Main.class"))
                    || !Files.isRegularFile(target.resolve("dev/nexus/launcher/update/UpdateHelper.class"))) {
                throw new IOException("The downloaded file is not a NEXUS Launcher package");
            }
        } catch (IOException e) {
            FileUtil.deleteRecursively(target);
            throw e;
        } finally {
            Files.deleteIfExists(zip);
        }
        return target;
    }

    /** Extracts a zip into {@code dest}, refusing entries that would escape it ("zip slip"). */
    private static void unzip(Path zip, Path dest) throws IOException {
        Path root = dest.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("Bad entry in the update package: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Starts the helper that swaps {@code app} and {@code newApp} once this process exits, then starts the
     * launcher again. The caller must exit the JVM right after this returns.
     */
    public static void startReplacement(Path newApp) throws IOException {
        Path app = currentAppDir();
        if (app == null) throw new IOException("The launcher is not running from an installed app folder");
        Path dir = updateDir();
        long pid = ProcessHandle.current().pid();
        // the helper runs from a copy of its own classes, so nothing inside app\ is in use during the swap
        Path helperRoot = dir.resolve("helper-" + pid);
        Path helperPkg = helperRoot.resolve("dev/nexus/launcher/update");
        Files.createDirectories(helperPkg);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(app.resolve("dev/nexus/launcher/update"), "UpdateHelper*.class")) {
            for (Path p : ds) Files.copy(p, helperPkg.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        }

        String java = javaExecutable();
        List<String> cmd = new ArrayList<>(List.of(java, "-Xmx64m", "-cp", helperRoot.toString(), UpdateHelper.class.getName(),
                Long.toString(pid), app.toString(), newApp.toString(), java));
        for (String prop : new String[]{"nexus.launcher.dir", "nexus.update.url"}) {
            String v = System.getProperty(prop);
            if (v != null && !v.isBlank()) cmd.add("-D" + prop + "=" + v);
        }
        Log.info("Starting the update helper: " + String.join(" ", cmd));
        new ProcessBuilder(cmd)
                .directory(app.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(dir.resolve("updater.log").toFile()))
                .start();
    }

    /** Deletes leftovers of earlier updates (helper copies, packages, the previous app folder). Busy files are skipped. */
    public static void cleanup() {
        Path dir = updateDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (name.startsWith("helper-") || name.endsWith(".zip") || name.endsWith(".download")) {
                        try {
                            FileUtil.deleteRecursively(p);
                        } catch (IOException ignored) {
                            // still in use (Windows) - next start
                        }
                    }
                }
            } catch (IOException e) {
                Log.warn("Cannot clean " + dir + ": " + e.getMessage());
            }
        }
        Path app = currentAppDir();
        if (app != null) {
            try {
                FileUtil.deleteRecursively(app.resolveSibling(APP_DIR + ".old"));
            } catch (IOException e) {
                Log.warn("Cannot delete the previous version: " + e.getMessage());
            }
        }
    }

    static Path updateDir() {
        return LauncherDirs.cache().resolve("update");
    }

    private static String javaExecutable() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        if (OS.isWindows()) {
            Path javaw = bin.resolve("javaw.exe");
            return (Files.exists(javaw) ? javaw : bin.resolve("java.exe")).toString();
        }
        return bin.resolve("java").toString();
    }

    // ---- download ----

    private static void download(UpdateManifest.FileRef ref, Path target, Progress progress) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".download");
        try {
            Http.download(ref.url(), tmp, progress::add);
            String actual = sha256(tmp);
            if (!actual.equalsIgnoreCase(ref.sha256())) {
                Log.warn("Checksum mismatch for " + ref.url() + ": expected " + ref.sha256() + ", got " + actual);
                throw new IOException(I18n.t("update.checksum", ref.url().substring(ref.url().lastIndexOf('/') + 1)));
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
