package dev.nexus.launcher.game;

import com.google.gson.JsonElement;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.net.Downloader;
import dev.nexus.launcher.net.Http;
import dev.nexus.launcher.net.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Version manifest and version files. */
public final class VersionRepository {
    public static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static volatile VersionManifest manifest;

    private VersionRepository() {}

    /**
     * Returns the version manifest. The network copy is cached on disk so the launcher keeps working offline
     * for versions that were installed before.
     */
    public static VersionManifest manifest(boolean refresh) throws IOException {
        VersionManifest m = manifest;
        if (m != null && !refresh) return m;
        Path cache = LauncherDirs.cache().resolve("version_manifest_v2.json");
        try {
            String text = Http.get(MANIFEST_URL);
            m = Json.parse(text, VersionManifest.class);
            if (m == null || m.versions == null) throw new IOException("Empty version manifest");
            Json.writeString(cache, text);
        } catch (IOException e) {
            if (!Files.exists(cache)) throw e;
            Log.warn("Version manifest unavailable, using the cached copy: " + e.getMessage());
            m = Json.read(cache, VersionManifest.class);
        }
        manifest = m;
        return m;
    }

    /** Makes sure versions/&lt;id&gt;/&lt;id&gt;.json of a Mojang version exists and matches the manifest. */
    public static void ensureVanillaJson(String id, Progress progress) throws IOException {
        Path file = LauncherDirs.versionJson(id);
        VersionManifest.Entry entry = null;
        try {
            entry = manifest(false).find(id);
        } catch (IOException e) {
            if (!Files.exists(file)) throw e;
        }
        if (entry == null) {
            if (Files.exists(file)) return;
            throw new IOException("Unknown Minecraft version: " + id);
        }
        Downloader.Task t = Downloader.Task.of(entry.url, file, entry.sha1, -1);
        if (Downloader.needsDownload(t)) Downloader.run(List.of(t), 1, progress);
    }

    public static boolean isInstalled(String id) {
        return Files.isRegularFile(LauncherDirs.versionJson(id));
    }

    public static VersionJson load(String id) throws IOException {
        Path file = LauncherDirs.versionJson(id);
        if (!Files.exists(file)) throw new IOException("Version file not found: " + file);
        VersionJson v = Json.read(file, VersionJson.class);
        if (v == null) throw new IOException("Empty version file: " + file);
        if (v.id == null) v.id = id;
        return v;
    }

    /**
     * Loads a version and its parents (downloading missing vanilla parents) and merges them into one
     * launchable description.
     */
    public static ResolvedVersion resolve(String id, Progress progress) throws IOException {
        List<VersionJson> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String cur = id;
        while (cur != null) {
            if (!seen.add(cur)) throw new IOException("Cyclic inheritsFrom in version " + id);
            if (!isInstalled(cur)) ensureVanillaJson(cur, progress);
            VersionJson v = load(cur);
            chain.add(v);
            cur = v.inheritsFrom;
        }
        // chain[0] = most specific, chain[last] = root
        VersionJson root = chain.get(chain.size() - 1);
        String mainClass = null, assetsId = null, legacyArgs = null, type = null, jarId = null;
        VersionJson.AssetIndexRef assetIndex = null;
        VersionJson.Download client = null;
        VersionJson.JavaVersion java = null;
        VersionJson.Logging logging = null;
        for (VersionJson v : chain) {
            if (mainClass == null) mainClass = v.mainClass;
            if (assetIndex == null) assetIndex = v.assetIndex;
            if (assetsId == null) assetsId = v.assets;
            if (type == null) type = v.type;
            if (java == null) java = v.javaVersion;
            if (logging == null) logging = v.logging;
            if (jarId == null) jarId = v.jar;
            if (client == null && v.downloads != null) client = v.downloads.get("client");
            if (legacyArgs == null && v.minecraftArguments != null) legacyArgs = v.minecraftArguments;
        }
        if (mainClass == null) throw new IOException("Version " + id + " has no main class");
        if (assetIndex != null && assetsId == null) assetsId = assetIndex.id;
        if (assetsId == null) assetsId = "legacy";

        // Libraries: child first, a library already provided by a more specific version wins.
        Map<String, Library> libs = new LinkedHashMap<>();
        for (VersionJson v : chain) {
            if (v.libraries == null) continue;
            for (Library l : v.libraries) {
                if (l == null || l.name == null) continue;
                String key;
                try {
                    key = l.coordinate().dedupeKey();
                } catch (IllegalArgumentException e) {
                    Log.warn("Skipping library with invalid name '" + l.name + "' in " + v.id);
                    continue;
                }
                // Legacy natives entries share their name with the plain artifact; keep both.
                if (l.natives != null) key += "#natives";
                libs.putIfAbsent(key, l);
            }
        }

        // Arguments: parent first, children append (a loader adds its own flags after vanilla ones).
        List<JsonElement> game = new ArrayList<>();
        List<JsonElement> jvm = new ArrayList<>();
        List<VersionJson> rootFirst = new ArrayList<>(chain);
        Collections.reverse(rootFirst);
        for (VersionJson v : rootFirst) {
            if (v.arguments == null) continue;
            if (v.arguments.game != null) game.addAll(v.arguments.game);
            if (v.arguments.jvm != null) jvm.addAll(v.arguments.jvm);
        }
        String baseId = jarId != null ? jarId : root.id;
        boolean rootModern = root.arguments != null;
        boolean modernJvm = rootModern && root.arguments.jvm != null && !root.arguments.jvm.isEmpty();
        if (rootModern && legacyArgs != null && game.isEmpty()) rootModern = false; // unusual: keep the string
        String legacy = rootModern ? null : (legacyArgs == null ? "" : legacyArgs);
        return new ResolvedVersion(id, baseId, type, mainClass, assetsId, assetIndex, client, java, logging,
                List.copyOf(libs.values()), game, jvm, legacy, modernJvm, chain.size() > 1);
    }
}
