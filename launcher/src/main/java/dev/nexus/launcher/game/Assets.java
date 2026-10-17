package dev.nexus.launcher.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.net.Downloader;
import dev.nexus.launcher.net.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Asset index and objects (sounds, languages, ...). */
public final class Assets {
    public static final String RESOURCES_URL = "https://resources.download.minecraft.net/";

    /** Parsed asset index. */
    public record Index(String id, boolean virtual, boolean mapToResources, List<Asset> objects) {}

    public record Asset(String path, String hash, long size) {
        public Path objectFile() {
            return LauncherDirs.assets().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
        }

        public String url() {
            return RESOURCES_URL + hash.substring(0, 2) + "/" + hash;
        }
    }

    private Assets() {}

    public static Path indexFile(String id) {
        return LauncherDirs.assets().resolve("indexes").resolve(id + ".json");
    }

    /** Downloads (if needed) and parses the asset index of a version. */
    public static Index loadIndex(ResolvedVersion v, Progress progress) throws IOException {
        Path file = indexFile(v.assetsId());
        VersionJson.AssetIndexRef ref = v.assetIndex();
        if (ref != null && ref.url != null) {
            Downloader.Task t = Downloader.Task.of(ref.url, file, ref.sha1, ref.size > 0 ? ref.size : -1);
            if (Downloader.needsDownload(t)) Downloader.run(List.of(t), 1, progress);
        }
        if (!Files.exists(file)) throw new IOException("Asset index " + v.assetsId() + " is missing");
        JsonObject o = Json.readObject(file);
        List<Asset> objects = new ArrayList<>();
        JsonObject objs = o.has("objects") ? o.getAsJsonObject("objects") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : objs.entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            objects.add(new Asset(e.getKey(), a.get("hash").getAsString(), a.has("size") ? a.get("size").getAsLong() : -1));
        }
        boolean virtual = o.has("virtual") && o.get("virtual").getAsBoolean();
        boolean map = o.has("map_to_resources") && o.get("map_to_resources").getAsBoolean();
        return new Index(v.assetsId(), virtual, map, objects);
    }

    public static List<Downloader.Task> tasks(Index index) {
        List<Downloader.Task> tasks = new ArrayList<>(index.objects().size());
        for (Asset a : index.objects()) {
            tasks.add(new Downloader.Task(a.url(), a.objectFile(), a.hash(), a.size(), false, false));
        }
        return tasks;
    }

    /** Directory passed as ${game_assets} for pre-1.7 versions. */
    public static Path virtualDir(String id) {
        return LauncherDirs.assets().resolve("virtual").resolve(id);
    }

    /**
     * Very old versions read assets by their real path: copy objects to assets/virtual/&lt;id&gt; ("virtual") or
     * to &lt;gameDir&gt;/resources ("map_to_resources").
     */
    public static void materializeLegacy(Index index, Path gameDir) throws IOException {
        if (!index.virtual() && !index.mapToResources()) return;
        Path base = index.mapToResources() ? gameDir.resolve("resources") : virtualDir(index.id());
        for (Asset a : index.objects()) {
            Path target = base.resolve(a.path()).normalize();
            if (!target.startsWith(base)) continue; // ignore paths escaping the folder
            if (Files.exists(target) && (a.size() < 0 || Files.size(target) == a.size())) continue;
            Files.createDirectories(target.getParent());
            Files.copy(a.objectFile(), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
