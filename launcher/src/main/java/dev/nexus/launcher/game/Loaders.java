package dev.nexus.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.net.Downloader;
import dev.nexus.launcher.net.Http;
import dev.nexus.launcher.net.Progress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Version lists and installation of mod loaders. */
public final class Loaders {
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String QUILT_META = "https://meta.quiltmc.org/v3";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String FORGE_PROMOS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    /** A loader version offered for a Minecraft version. */
    public record LoaderVersion(String version, boolean stable, String tag) {
        @Override
        public String toString() {
            return tag == null ? version : version + "  (" + tag + ")";
        }
    }

    private Loaders() {}

    /** Loader versions for a Minecraft version, newest first. Empty when the loader does not support it. */
    public static List<LoaderVersion> versions(LoaderType type, String mc) throws IOException {
        return switch (type) {
            case VANILLA -> List.of();
            case FABRIC -> fabricLike(FABRIC_META + "/versions/loader/" + Http.enc(mc));
            case QUILT -> fabricLike(QUILT_META + "/versions/loader/" + Http.enc(mc));
            case FORGE -> forge(mc);
            case NEOFORGE -> neoForge(mc);
        };
    }

    private static List<LoaderVersion> fabricLike(String url) throws IOException {
        List<LoaderVersion> out = new ArrayList<>();
        JsonElement e;
        try {
            e = Http.getJson(url);
        } catch (dev.nexus.launcher.net.HttpStatusException ex) {
            if (ex.status() == 400 || ex.status() == 404) return out;
            throw ex;
        }
        if (!e.isJsonArray()) return out;
        for (JsonElement item : e.getAsJsonArray()) {
            JsonObject loader = item.getAsJsonObject().getAsJsonObject("loader");
            String v = loader.get("version").getAsString();
            boolean stable = !loader.has("stable") || loader.get("stable").getAsBoolean();
            if (!loader.has("stable") && (v.contains("beta") || v.contains("pre") || v.contains("rc"))) stable = false;
            out.add(new LoaderVersion(v, stable, null));
        }
        // Quilt meta returns versions unordered; Fabric's order is not guaranteed either.
        out.sort((a, b) -> compareVersions(b.version(), a.version()));
        return out;
    }

    /**
     * Compares dotted versions with optional pre-release suffixes ("0.20.0-beta.9", "1.2.3+build.4").
     * Numeric parts compare numerically; a release is newer than its pre-releases.
     */
    public static int compareVersions(String a, String b) {
        String[] am = a.split("\\+", 2)[0].split("-", 2), bm = b.split("\\+", 2)[0].split("-", 2);
        int c = compareParts(am[0], bm[0]);
        if (c != 0) return c;
        boolean ap = am.length > 1, bp = bm.length > 1;
        if (ap != bp) return ap ? -1 : 1;
        return ap ? compareParts(am[1], bm[1]) : 0;
    }

    private static int compareParts(String a, String b) {
        String[] x = a.split("[.]"), y = b.split("[.]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0", q = i < y.length ? y[i] : "0";
            boolean pn = p.matches("\\d{1,9}"), qn = q.matches("\\d{1,9}");
            int c = pn && qn ? Integer.compare(Integer.parseInt(p), Integer.parseInt(q))
                    : pn ? 1 : qn ? -1 : p.compareTo(q);
            if (c != 0) return c;
        }
        return 0;
    }

    private static List<LoaderVersion> forge(String mc) throws IOException {
        List<String> all = mavenVersions(FORGE_MAVEN + "maven-metadata.xml");
        String recommended = null, latest = null;
        try {
            JsonObject promos = Http.getJson(FORGE_PROMOS).getAsJsonObject().getAsJsonObject("promos");
            recommended = Json.str(promos, mc + "-recommended", null);
            latest = Json.str(promos, mc + "-latest", null);
        } catch (IOException e) {
            Log.warn("Forge promotions unavailable: " + e.getMessage());
        }
        List<LoaderVersion> out = new ArrayList<>();
        String prefix = mc + "-";
        for (String v : all) {
            if (!v.startsWith(prefix)) continue;
            String forgeVersion = v.substring(prefix.length());
            String tag = forgeVersion.equals(recommended) || forgeVersion.startsWith(recommended + "-") ? I18n.t("loader.recommended")
                    : forgeVersion.equals(latest) || forgeVersion.startsWith(latest + "-") ? I18n.t("loader.latest") : null;
            out.add(new LoaderVersion(v, recommended != null && tag != null && tag.equals(I18n.t("loader.recommended")), tag));
        }
        Collections.reverse(out);
        return out;
    }

    private static List<LoaderVersion> neoForge(String mc) throws IOException {
        String prefix;
        if (mc.startsWith("1.")) {
            String[] p = mc.substring(2).split("\\.");
            if (p.length == 0) return List.of();
            prefix = p[0] + "." + (p.length > 1 ? p[1] : "0") + ".";
        } else {
            prefix = mc + ".";
        }
        List<LoaderVersion> out = new ArrayList<>();
        for (String v : mavenVersions(NEOFORGE_MAVEN + "maven-metadata.xml")) {
            if (v.startsWith(prefix)) out.add(new LoaderVersion(v, !v.contains("beta") && !v.contains("alpha"), v.contains("beta") ? "beta" : null));
        }
        Collections.reverse(out);
        return out;
    }

    private static final Pattern MAVEN_VERSION = Pattern.compile("<version>([^<]+)</version>");

    private static List<String> mavenVersions(String url) throws IOException {
        String xml = Http.get(url);
        List<String> out = new ArrayList<>();
        Matcher m = MAVEN_VERSION.matcher(xml);
        while (m.find()) out.add(m.group(1).trim());
        return out;
    }

    /** Version id that {@link #install} creates (without installing anything). */
    public static String expectedVersionId(LoaderType type, String mc, String loader) {
        return switch (type) {
            case VANILLA -> mc;
            case FABRIC -> "fabric-loader-" + loader + "-" + mc;
            case QUILT -> "quilt-loader-" + loader + "-" + mc;
            case FORGE, NEOFORGE -> null; // decided by the installer's install_profile.json
        };
    }

    /**
     * Installs a loader into the shared versions/libraries folders and returns the id of the created version.
     *
     * @param java  Java used to run Forge/NeoForge installers
     * @param log   receives installer output lines
     */
    public static String install(LoaderType type, String mc, String loader, Path java, Progress progress, Consumer<String> log) throws IOException {
        progress.stage(I18n.t("stage.loader", type.display() + " " + loader));
        return switch (type) {
            case VANILLA -> mc;
            case FABRIC -> installProfile(FABRIC_META + "/versions/loader/" + Http.enc(mc) + "/" + Http.enc(loader) + "/profile/json");
            case QUILT -> installProfile(QUILT_META + "/versions/loader/" + Http.enc(mc) + "/" + Http.enc(loader) + "/profile/json");
            case FORGE -> runInstaller(FORGE_MAVEN + loader + "/forge-" + loader + "-installer.jar", java, progress, log);
            case NEOFORGE -> runInstaller(NEOFORGE_MAVEN + loader + "/neoforge-" + loader + "-installer.jar", java, progress, log);
        };
    }

    private static String installProfile(String url) throws IOException {
        String text = Http.get(url);
        JsonObject profile = Json.parseObject(text);
        String id = profile.get("id").getAsString();
        Json.writeString(LauncherDirs.versionJson(id), text);
        Log.info("Installed loader profile " + id);
        return id;
    }

    /**
     * Runs an official Forge/NeoForge installer in headless mode against the launcher root. The root has the
     * .minecraft layout, so the installer writes versions/ and libraries/ exactly where the launcher reads them.
     */
    private static String runInstaller(String url, Path java, Progress progress, Consumer<String> log) throws IOException {
        Path installer = LauncherDirs.cache().resolve("installers").resolve(url.substring(url.lastIndexOf('/') + 1));
        Downloader.Task task = Downloader.Task.of(url, installer, null, -1);
        if (!Files.isRegularFile(installer)) Downloader.run(List.of(task), 1, progress);

        String versionId = installerVersionId(installer);
        if (versionId == null) throw new IOException(I18n.t("error.loader.installer.unknown"));

        // The installers refuse to run without a launcher profile file.
        Path profiles = LauncherDirs.root().resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) Json.writeString(profiles, "{\n  \"profiles\": {}\n}\n");

        progress.stage(I18n.t("stage.installer"));
        progress.total(0);
        Path javaCli = java;
        if (java.getFileName().toString().equalsIgnoreCase("javaw.exe")) {
            Path alt = java.resolveSibling("java.exe");
            if (Files.exists(alt)) javaCli = alt;
        }
        List<String> cmd = List.of(javaCli.toString(), "-jar", installer.toString(), "--installClient", LauncherDirs.root().toString());
        Log.info("Running installer: " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(installer.getParent().toFile()).redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (progress.isCancelled()) {
                    p.destroyForcibly();
                    throw new InterruptedIOException("Cancelled");
                }
                progress.detail(line.length() > 120 ? line.substring(0, 120) + "…" : line);
                if (log != null) log.accept(line);
            }
        }
        int code;
        try {
            code = p.waitFor();
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Cancelled");
        }
        if (code != 0 || !VersionRepository.isInstalled(versionId)) {
            throw new IOException(I18n.t("error.loader.installer.failed", code));
        }
        Log.info("Installer finished, created version " + versionId);
        return versionId;
    }

    /** Reads the version id the installer will create from its install_profile.json. */
    static String installerVersionId(Path installer) throws IOException {
        try (ZipFile zip = new ZipFile(installer.toFile())) {
            ZipEntry e = zip.getEntry("install_profile.json");
            if (e == null) return null;
            JsonObject o = Json.parseObject(new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
            if (o.has("version") && o.get("version").isJsonPrimitive()) return o.get("version").getAsString();
            if (o.has("install") && o.getAsJsonObject("install").has("target")) {
                return o.getAsJsonObject("install").get("target").getAsString(); // pre-1.13 installers
            }
            return null;
        }
    }

    /** Minecraft versions the Fabric/Quilt meta knows about (used to grey out unsupported versions). */
    public static List<String> supportedGameVersions(LoaderType type) throws IOException {
        String url = switch (type) {
            case FABRIC -> FABRIC_META + "/versions/game";
            case QUILT -> QUILT_META + "/versions/game";
            default -> null;
        };
        if (url == null) return List.of();
        List<String> out = new ArrayList<>();
        JsonArray arr = Http.getJson(url).getAsJsonArray();
        for (JsonElement e : arr) out.add(e.getAsJsonObject().get("version").getAsString());
        return out;
    }
}
