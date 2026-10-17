package dev.nexus.launcher.update;

import dev.nexus.launcher.Main;
import dev.nexus.launcher.core.BuildInfo;
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
import java.util.zip.ZipFile;

/**
 * Update checks and installation for the launcher itself and for NEXUS Client.
 * Nothing is installed without the user's consent: {@link #check()} only reads the manifest, the UI asks,
 * and only then {@link #installClient} / {@link #downloadLauncher} run.
 */
public final class Updates {

    /** A NEXUS Client jar to download for one Minecraft version. */
    public record ClientFile(String mc, UpdateManifest.FileRef file) {}

    /**
     * Result of a check. {@code launcherVersion} is null when the launcher is up to date, {@code launcherFile} is
     * null when the release has no jar for this platform. {@code clientFiles} is empty when NEXUS Client is up to date.
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

    /** Downloads the new NEXUS Client jars and updates every instance that has NEXUS Client installed. */
    public static NexusClientStore.SyncResult installClient(Check c, Progress progress) throws IOException {
        long total = c.clientFiles().stream().mapToLong(f -> Math.max(0, f.file().size())).sum();
        progress.set(0);
        progress.total(total > 0 ? total : -1);
        Files.createDirectories(NexusClientStore.dir());
        for (ClientFile f : c.clientFiles()) {
            progress.checkCancelled();
            progress.detail("NEXUS Client " + c.clientVersion() + " (" + f.mc() + ")");
            Path target = NexusClientStore.dir().resolve(NexusClientStore.Source.fileName(c.clientVersion(), f.mc()));
            download(f.file(), target, progress);
            NexusClientStore.pruneOlder(f.mc(), c.clientVersion());
        }
        return NexusClientStore.syncAll();
    }

    // ---- launcher ----

    /** Platform key of the running launcher jar in the manifest ("win", "linux", "mac", "mac-aarch64"). */
    public static String platformKey() {
        boolean arm = OS.ARCH.equals("arm64");
        return switch (OS.TYPE) {
            case WINDOWS -> "win";
            case MACOS -> arm ? "mac-aarch64" : "mac";
            default -> arm ? "linux-aarch64" : "linux";
        };
    }

    /** The jar the launcher is running from, or null (for example when started from an IDE or Gradle). */
    public static Path currentJar() {
        try {
            Path p = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jar") ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Why the launcher cannot replace itself ("notJar", "readOnly"), or null when it can. */
    public static String selfUpdateBlocker() {
        Path jar = currentJar();
        if (jar == null) return "notJar";
        Path dir = jar.getParent();
        try {
            Path probe = Files.createTempFile(dir, ".nexus-write-test", ".tmp");
            Files.delete(probe);
        } catch (IOException e) {
            return "readOnly";
        }
        return Files.isWritable(jar) ? null : "readOnly";
    }

    /** Downloads and verifies the new launcher jar. */
    public static Path downloadLauncher(Check c, Progress progress) throws IOException {
        if (c.launcherFile() == null) throw new IOException("The release has no launcher build for " + platformKey());
        Path dir = updateDir();
        Files.createDirectories(dir);
        Path target = dir.resolve("nexus-launcher-" + c.launcherVersion() + ".jar");
        progress.set(0);
        progress.total(c.launcherFile().size() > 0 ? c.launcherFile().size() : -1);
        progress.detail("NEXUS Launcher " + c.launcherVersion());
        download(c.launcherFile(), target, progress);
        try (ZipFile zip = new ZipFile(target.toFile())) {
            if (zip.getEntry("dev/nexus/launcher/Main.class") == null || zip.getEntry("dev/nexus/launcher/update/UpdateHelper.class") == null) {
                throw new IOException("The downloaded file is not a NEXUS Launcher jar");
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return target;
    }

    /**
     * Starts the helper that replaces the running jar with {@code newJar} once this process exits, then
     * starts the launcher again. The caller must exit the JVM right after this returns.
     */
    public static void startReplacement(Path newJar) throws IOException {
        Path jar = currentJar();
        if (jar == null) throw new IOException("The launcher is not running from a jar");
        Path dir = updateDir();
        Files.createDirectories(dir);
        long pid = ProcessHandle.current().pid();
        // the helper runs from a copy, so it does not lock the jar it replaces
        Path helperJar = dir.resolve("updater-" + pid + ".jar");
        Files.copy(jar, helperJar, StandardCopyOption.REPLACE_EXISTING);

        String java = javaExecutable();
        List<String> cmd = new ArrayList<>(List.of(java, "-Xmx64m", "-cp", helperJar.toString(), UpdateHelper.class.getName(),
                Long.toString(pid), jar.toString(), newJar.toString(), java));
        for (String prop : new String[]{"nexus.launcher.dir", "nexus.update.url"}) {
            String v = System.getProperty(prop);
            if (v != null && !v.isBlank()) cmd.add("-D" + prop + "=" + v);
        }
        Log.info("Starting the update helper: " + String.join(" ", cmd));
        new ProcessBuilder(cmd)
                .directory(jar.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(dir.resolve("updater.log").toFile()))
                .start();
    }

    /** Deletes leftovers of earlier updates (helper copies, downloaded jars). Files still in use are skipped. */
    public static void cleanup() {
        Path dir = updateDir();
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path p : ds) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // still running (Windows) — next start
                }
            }
        } catch (IOException e) {
            Log.warn("Cannot clean " + dir + ": " + e.getMessage());
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
