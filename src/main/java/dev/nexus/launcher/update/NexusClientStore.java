package dev.nexus.launcher.update;

import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.game.LoaderType;
import dev.nexus.launcher.game.Loaders;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.instance.ModManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All NEXUS Client jars the launcher can install: the ones bundled inside the launcher jar plus newer ones
 * downloaded by the updater into {@code <data>/nexus-client/}. For every Minecraft version the newest jar wins.
 */
public final class NexusClientStore {
    /** Mod id from fabric.mod.json of NEXUS Client. */
    public static final String MOD_ID = "nexus";
    private static final Pattern CACHED = Pattern.compile("nexus-client-(.+)\\+mc(.+)\\.jar");

    /** One NEXUS Client jar: either a launcher resource ({@code file == null}) or a downloaded file. */
    public record Source(String version, String mc, Path file) {
        public String fileName() {
            return fileName(version, mc);
        }

        static String fileName(String version, String mc) {
            return "nexus-client-" + version + "+mc" + mc.toLowerCase(Locale.ROOT) + ".jar";
        }

        public InputStream open() throws IOException {
            if (file != null) return Files.newInputStream(file);
            InputStream in = NexusClientStore.class.getResourceAsStream("/bundled/" + fileName());
            if (in == null) throw new IOException("Bundled file missing: " + fileName());
            return in;
        }
    }

    private NexusClientStore() {}

    public static Path dir() {
        return LauncherDirs.root().resolve("nexus-client");
    }

    /** Newest NEXUS Client for the given loader and Minecraft version, or null when there is none. */
    public static Source best(LoaderType loader, String mc) {
        if (loader != LoaderType.FABRIC || mc == null) return null;
        Source best = null;
        String bundledName = Source.fileName(BuildInfo.NEXUS_CLIENT, mc);
        if (NexusClientStore.class.getResource("/bundled/" + bundledName) != null) {
            best = new Source(BuildInfo.NEXUS_CLIENT, mc, null);
        }
        for (Source s : cached()) {
            if (s.mc().equalsIgnoreCase(mc) && (best == null || Loaders.compareVersions(s.version(), best.version()) > 0)) best = s;
        }
        return best;
    }

    public static Source best(Instance inst) {
        return best(inst.loaderType(), inst.mcVersion);
    }

    /** Newest NEXUS Client version the launcher has (bundled or downloaded), for display. */
    public static String latestKnownVersion() {
        String v = BuildInfo.NEXUS_CLIENT;
        for (Source s : cached()) if (Loaders.compareVersions(s.version(), v) > 0) v = s.version();
        return v;
    }

    /** True when a jar of at least {@code version} is available for {@code mc}. */
    static boolean has(String mc, String version) {
        Source s = best(LoaderType.FABRIC, mc);
        return s != null && Loaders.compareVersions(s.version(), version) >= 0;
    }

    /** Downloaded jars in {@code <data>/nexus-client/}. */
    static List<Source> cached() {
        List<Source> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir(), "nexus-client-*.jar")) {
            for (Path p : ds) {
                Matcher m = CACHED.matcher(p.getFileName().toString());
                if (m.matches() && Files.isRegularFile(p)) out.add(new Source(m.group(1), m.group(2), p));
            }
        } catch (IOException e) {
            Log.warn("Cannot list " + dir() + ": " + e.getMessage());
        }
        return out;
    }

    /** Removes downloaded jars that are older than {@code keepVersion} for the same Minecraft version. */
    static void pruneOlder(String mc, String keepVersion) {
        for (Source s : cached()) {
            if (s.mc().equalsIgnoreCase(mc) && Loaders.compareVersions(s.version(), keepVersion) < 0) {
                try {
                    Files.deleteIfExists(s.file());
                } catch (IOException e) {
                    Log.warn("Cannot delete " + s.file() + ": " + e.getMessage());
                }
            }
        }
    }

    /** NEXUS Client jar installed in the instance (enabled or disabled), or null. */
    public static ModManager.ModFile installed(Instance inst) {
        for (ModManager.ModFile m : ModManager.list(inst)) {
            if (isNexus(m)) return m;
        }
        return null;
    }

    public static boolean isNexus(ModManager.ModFile m) {
        return MOD_ID.equals(m.id()) || m.baseName().toLowerCase(Locale.ROOT).startsWith("nexus-client");
    }

    /** True when the instance has NEXUS Client and a newer jar is available. */
    public static boolean updateAvailable(Instance inst) {
        ModManager.ModFile m = installed(inst);
        Source s = m == null ? null : best(inst);
        return s != null && (m.version() == null || Loaders.compareVersions(s.version(), m.version()) > 0);
    }

    /**
     * Installs {@code source} into the instance, replacing any NEXUS Client jar already there. A disabled
     * NEXUS Client stays disabled.
     */
    public static Path install(Instance inst, Source source) throws IOException {
        Files.createDirectories(inst.modsDir());
        boolean enabled = true;
        List<ModManager.ModFile> old = new ArrayList<>();
        for (ModManager.ModFile m : ModManager.list(inst)) {
            if (isNexus(m)) {
                old.add(m);
                enabled &= m.enabled();
            }
        }
        Path target = inst.modsDir().resolve(source.fileName() + (enabled ? "" : ".disabled"));
        Path tmp = inst.modsDir().resolve(source.fileName() + ".tmp");
        try (InputStream in = source.open()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        for (ModManager.ModFile m : old) {
            if (!m.file().equals(target)) Files.deleteIfExists(m.file());
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
     * Updates NEXUS Client in the instance when a newer jar is available. Returns the new version or null when
     * nothing changed. Called before every launch, so instances that were running during an update catch up.
     */
    public static String sync(Instance inst) throws IOException {
        if (!updateAvailable(inst)) return null;
        Source s = best(inst);
        install(inst, s);
        Log.info("NEXUS Client in " + inst.id + " updated to " + s.version());
        return s.version();
    }

    /** Result of {@link #syncAll()}: updated instances and instances skipped because the game is running. */
    public record SyncResult(List<String> updated, List<String> skipped) {}

    public static SyncResult syncAll() {
        List<String> updated = new ArrayList<>(), skipped = new ArrayList<>();
        for (Instance inst : InstanceManager.all()) {
            try {
                if (!updateAvailable(inst)) continue;
                if (GameProcess.running(inst) != null) {
                    skipped.add(inst.name);
                    continue;
                }
                if (sync(inst) != null) updated.add(inst.name);
            } catch (IOException e) {
                Log.warn("Cannot update NEXUS Client in " + inst.id + ": " + e.getMessage());
                skipped.add(inst.name);
            }
        }
        return new SyncResult(updated, skipped);
    }
}
