package dev.nexus.launcher.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.Settings;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Contents of {@code nexus-update.json}, the single file that describes the latest release:
 *
 * <pre>
 * {
 *   "schema": 1,
 *   "page": "https://github.com/me/nexus/releases/latest",          (optional, opened by "Download")
 *   "launcher": {
 *     "version": "1.1.0",
 *     "changelog": "…",
 *     "files": { "win": {"url": "nexus-launcher-1.1.0-win.jar", "sha256": "…", "size": 123}, "linux": …,
 *                "mac": …, "mac-aarch64": … }
 *   },
 *   "client": {
 *     "version": "1.1.0",
 *     "changelog": "…",
 *     "files": { "1.21.11": {"url": "nexus-client-1.1.0+mc1.21.11.jar", "sha256": "…", "size": 123}, … }
 *   }
 * }
 * </pre>
 *
 * Relative URLs are resolved against the address of the manifest, so a GitHub release only needs the files
 * uploaded next to the manifest. {@code ./gradlew release} generates this file.
 */
public record UpdateManifest(String page, Component launcher, Component client) {

    public record FileRef(String url, String sha256, long size) {}

    public record Component(String version, String changelog, Map<String, FileRef> files) {}

    /** Address of nexus-update.json, or null when updates are not configured. */
    public static String address() {
        String env = System.getProperty("nexus.update.url");
        if (blank(env)) env = System.getenv("NEXUS_UPDATE_URL");
        if (!blank(env)) return env.trim();
        Settings s = Settings.get();
        if (s != null && !blank(s.updateUrl)) return s.updateUrl.trim();
        return builtInAddress();
    }

    /** Address compiled into the launcher (gradle.properties), or null. */
    public static String builtInAddress() {
        if (!blank(BuildInfo.UPDATE_URL)) return BuildInfo.UPDATE_URL;
        if (!blank(BuildInfo.UPDATE_REPO)) return "https://github.com/" + BuildInfo.UPDATE_REPO + "/releases/latest/download/nexus-update.json";
        return null;
    }

    /** Release page for manual downloads: manifest "page", the GitHub releases page, or null. */
    public String releasePage() {
        if (!blank(page)) return page;
        if (!blank(BuildInfo.UPDATE_REPO)) return "https://github.com/" + BuildInfo.UPDATE_REPO + "/releases/latest";
        return null;
    }

    static UpdateManifest parse(JsonObject o, String manifestUrl) throws IOException {
        int schema = o.has("schema") ? o.get("schema").getAsInt() : 1;
        if (schema > 1) throw new IOException("Unsupported nexus-update.json schema " + schema + ", update the launcher manually");
        return new UpdateManifest(str(o, "page"), component(o, "launcher", manifestUrl), component(o, "client", manifestUrl));
    }

    private static Component component(JsonObject root, String key, String base) throws IOException {
        if (!root.has(key) || !root.get(key).isJsonObject()) return null;
        JsonObject c = root.getAsJsonObject(key);
        String version = str(c, "version");
        if (blank(version)) throw new IOException("\"" + key + ".version\" is missing in nexus-update.json");
        Map<String, FileRef> files = new LinkedHashMap<>();
        if (c.has("files") && c.get("files").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : c.getAsJsonObject("files").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject f = e.getValue().getAsJsonObject();
                String url = str(f, "url"), sha = str(f, "sha256");
                if (blank(url) || blank(sha) || !sha.matches("(?i)[0-9a-f]{64}")) {
                    throw new IOException("\"" + key + ".files." + e.getKey() + "\" needs \"url\" and a SHA-256 \"sha256\"");
                }
                long size = f.has("size") ? f.get("size").getAsLong() : -1;
                files.put(e.getKey(), new FileRef(resolve(base, url), sha.toLowerCase(Locale.ROOT), size));
            }
        }
        return new Component(version.trim(), str(c, "changelog"), files);
    }

    private static String resolve(String base, String url) throws IOException {
        try {
            return URI.create(base).resolve(url.trim().replace(" ", "%20")).toString();
        } catch (IllegalArgumentException e) {
            throw new IOException("Bad URL in nexus-update.json: " + url);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
