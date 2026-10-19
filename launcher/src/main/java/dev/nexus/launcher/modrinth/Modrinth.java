package dev.nexus.launcher.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.game.LoaderType;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.net.Downloader;
import dev.nexus.launcher.net.Http;
import dev.nexus.launcher.net.HttpStatusException;
import dev.nexus.launcher.net.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Modrinth API v2 (public, no key required). */
public final class Modrinth {
    private static final String API = "https://api.modrinth.com/v2";
    public static final String FABRIC_API_ID = "P7dR8mSH";

    public enum Sort {
        RELEVANCE("relevance"), DOWNLOADS("downloads"), FOLLOWS("follows"), NEWEST("newest"), UPDATED("updated");
        public final String index;

        Sort(String index) { this.index = index; }
    }

    public record Hit(String projectId, String slug, String title, String description, String author, String iconUrl, long downloads) {}

    public record SearchResult(List<Hit> hits, int offset, int total) {}

    public record ModFileRef(String url, String fileName, String sha1, long size) {}

    public record Dependency(String projectId, String versionId, String type) {}

    public record Version(String id, String projectId, String name, String versionNumber, String type, ModFileRef file, List<Dependency> dependencies) {}

    private Modrinth() {}

    /** Loaders whose mods an instance can run (Quilt also runs Fabric mods). */
    public static List<String> loadersFor(LoaderType t) {
        return switch (t) {
            case QUILT -> List.of("quilt", "fabric");
            case VANILLA -> List.of();
            default -> List.of(t.modrinthId());
        };
    }

    public static SearchResult search(String query, Instance inst, Sort sort, int offset, int limit) throws IOException {
        JsonArray facets = new JsonArray();
        JsonArray loaders = new JsonArray();
        for (String l : loadersFor(inst.loaderType())) loaders.add("categories:" + l);
        if (!loaders.isEmpty()) facets.add(loaders);
        JsonArray versions = new JsonArray();
        versions.add("versions:" + inst.mcVersion);
        facets.add(versions);
        JsonArray type = new JsonArray();
        type.add("project_type:mod");
        facets.add(type);
        String url = API + "/search?query=" + Http.enc(query == null ? "" : query.trim())
                + "&facets=" + Http.enc(facets.toString()) + "&index=" + sort.index
                + "&offset=" + offset + "&limit=" + limit;
        JsonObject o = Http.getJson(url).getAsJsonObject();
        List<Hit> hits = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("hits")) {
            JsonObject h = e.getAsJsonObject();
            String icon = Json.str(h, "icon_url", null);
            hits.add(new Hit(h.get("project_id").getAsString(), Json.str(h, "slug", null), Json.str(h, "title", "?"),
                    Json.str(h, "description", ""), Json.str(h, "author", ""), icon == null || icon.isBlank() ? null : icon,
                    h.has("downloads") ? h.get("downloads").getAsLong() : 0));
        }
        return new SearchResult(hits, o.get("offset").getAsInt(), o.get("total_hits").getAsInt());
    }

    /** Newest version of a project compatible with the instance, preferring releases; null when none. */
    public static Version latestCompatible(String projectId, Instance inst) throws IOException {
        JsonArray loaders = new JsonArray();
        for (String l : loadersFor(inst.loaderType())) loaders.add(l);
        JsonArray game = new JsonArray();
        game.add(inst.mcVersion);
        String url = API + "/project/" + Http.enc(projectId) + "/version?loaders=" + Http.enc(loaders.toString())
                + "&game_versions=" + Http.enc(game.toString());
        JsonArray arr;
        try {
            arr = Http.getJson(url).getAsJsonArray();
        } catch (HttpStatusException e) {
            if (e.status() == 404) return null;
            throw e;
        }
        Version firstAny = null;
        for (JsonElement e : arr) {
            Version v = parseVersion(e.getAsJsonObject());
            if (v.file() == null) continue;
            if (firstAny == null) firstAny = v;
            if ("release".equals(v.type())) return v;
        }
        return firstAny;
    }

    public static Version version(String versionId) throws IOException {
        return parseVersion(Http.getJson(API + "/version/" + Http.enc(versionId)).getAsJsonObject());
    }

    public static String projectTitle(String projectId) {
        try {
            return Json.str(Http.getJson(API + "/project/" + Http.enc(projectId)).getAsJsonObject(), "title", projectId);
        } catch (IOException e) {
            return projectId;
        }
    }

    /** Installed project ids of an instance (only files that still exist). */
    public static Set<String> installedProjects(Instance inst) {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, Instance.ModrinthRef> e : inst.modrinth.entrySet()) {
            Path f = inst.modsDir().resolve(e.getKey());
            if (Files.exists(f) || Files.exists(f.resolveSibling(e.getKey() + ".disabled"))) ids.add(e.getValue().projectId);
        }
        return ids;
    }

    /**
     * Installs the newest compatible version of a project and, recursively, its required dependencies that are
     * not installed yet. Returns the titles of everything that was installed.
     */
    public static List<String> install(Instance inst, String projectId, String title, Progress progress) throws IOException {
        List<String> installed = new ArrayList<>();
        Set<String> visited = new HashSet<>(installedProjects(inst));
        installRecursive(inst, projectId, null, title, visited, installed, progress, 0);
        InstanceManager.save(inst);
        return installed;
    }

    private static void installRecursive(Instance inst, String projectId, String versionId, String title, Set<String> visited,
                                         List<String> installed, Progress progress, int depth) throws IOException {
        if (depth > 8 || (projectId != null && !visited.add(projectId))) return;
        progress.checkCancelled();
        Version v = versionId != null ? version(versionId) : latestCompatible(projectId, inst);
        if (v == null) {
            if (depth == 0) throw new IOException(dev.nexus.launcher.i18n.I18n.t("modrinth.noVersion", inst.describe()));
            Log.warn("Dependency " + projectId + " has no version for " + inst.describe());
            return;
        }
        if (projectId == null) {
            if (!visited.add(v.projectId())) return;
        }
        String name = title != null ? title : projectTitle(v.projectId());
        progress.stage(dev.nexus.launcher.i18n.I18n.t("stage.mod", name));
        Path target = inst.modsDir().resolve(v.file().fileName());
        Downloader.run(List.of(Downloader.Task.of(v.file().url(), target, v.file().sha1(), v.file().size())), 1, progress);
        removeOtherFilesOf(inst, v.projectId(), v.file().fileName());
        inst.modrinth.put(v.file().fileName(), new Instance.ModrinthRef(v.projectId(), v.id(), name));
        installed.add(name);
        for (Dependency d : v.dependencies()) {
            if (!"required".equals(d.type())) continue;
            installRecursive(inst, d.projectId(), d.projectId() == null ? d.versionId() : null, null, visited, installed, progress, depth + 1);
        }
    }

    /** Deletes older files of the same project (used by updates and re-installs). */
    private static void removeOtherFilesOf(Instance inst, String projectId, String keepFile) throws IOException {
        for (var it = inst.modrinth.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().projectId.equals(projectId) && !e.getKey().equals(keepFile)) {
                Files.deleteIfExists(inst.modsDir().resolve(e.getKey()));
                Files.deleteIfExists(inst.modsDir().resolve(e.getKey() + ".disabled"));
                it.remove();
            }
        }
    }

    /** An available update for an installed file. */
    public record Update(String fileName, String title, Version version) {}

    public static List<Update> checkUpdates(Instance inst, Progress progress) throws IOException {
        List<Update> updates = new ArrayList<>();
        for (Map.Entry<String, Instance.ModrinthRef> e : List.copyOf(inst.modrinth.entrySet())) {
            progress.checkCancelled();
            if (!Files.exists(inst.modsDir().resolve(e.getKey()))) continue; // disabled or removed
            Version latest = latestCompatible(e.getValue().projectId, inst);
            if (latest != null && !latest.id().equals(e.getValue().versionId)) {
                updates.add(new Update(e.getKey(), e.getValue().title, latest));
            }
        }
        return updates;
    }

    public static void applyUpdate(Instance inst, Update u, Progress progress) throws IOException {
        Path target = inst.modsDir().resolve(u.version().file().fileName());
        Downloader.run(List.of(Downloader.Task.of(u.version().file().url(), target, u.version().file().sha1(), u.version().file().size())), 1, progress);
        removeOtherFilesOf(inst, u.version().projectId(), u.version().file().fileName());
        inst.modrinth.put(u.version().file().fileName(), new Instance.ModrinthRef(u.version().projectId(), u.version().id(), u.title()));
        InstanceManager.save(inst);
    }

    private static Version parseVersion(JsonObject o) {
        ModFileRef file = null;
        JsonArray files = o.getAsJsonArray("files");
        if (files != null) {
            for (JsonElement fe : files) {
                JsonObject f = fe.getAsJsonObject();
                boolean primary = f.has("primary") && f.get("primary").getAsBoolean();
                if (file == null || primary) {
                    JsonObject hashes = f.getAsJsonObject("hashes");
                    file = new ModFileRef(f.get("url").getAsString(), f.get("filename").getAsString(),
                            hashes != null ? Json.str(hashes, "sha1", null) : null, f.has("size") ? f.get("size").getAsLong() : -1);
                    if (primary) break;
                }
            }
        }
        List<Dependency> deps = new ArrayList<>();
        if (o.has("dependencies") && o.get("dependencies").isJsonArray()) {
            for (JsonElement de : o.getAsJsonArray("dependencies")) {
                JsonObject d = de.getAsJsonObject();
                deps.add(new Dependency(Json.str(d, "project_id", null), Json.str(d, "version_id", null), Json.str(d, "dependency_type", "")));
            }
        }
        return new Version(o.get("id").getAsString(), o.get("project_id").getAsString(), Json.str(o, "name", ""),
                Json.str(o, "version_number", ""), Json.str(o, "version_type", "release"), file, deps);
    }
}
