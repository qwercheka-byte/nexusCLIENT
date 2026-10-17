package dev.nexus.launcher.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.nexus.launcher.auth.Account;
import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.net.Downloader;
import dev.nexus.launcher.net.Progress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prepares everything an instance needs and builds its command line. */
public final class GameLauncher {
    private static final String LIBRARIES_URL = "https://libraries.minecraft.net/";

    /** Ready-to-run launch. */
    public record LaunchSpec(List<String> command, Path workingDir, ResolvedVersion version, JavaRuntimes.JavaInstall java) {}

    private GameLauncher() {}

    public static LaunchSpec prepare(Instance inst, Account account, Progress progress, Consumer<String> log) throws IOException {
        Settings settings = Settings.get();
        Files.createDirectories(inst.gameDir());

        // 0. NEXUS Client: pick up a newer jar downloaded by the updater (e.g. the game was running during the update)
        try {
            String updated = dev.nexus.launcher.update.NexusClientStore.sync(inst);
            if (updated != null) log.accept("NEXUS Client updated to " + updated);
        } catch (IOException e) {
            Log.warn("Cannot update NEXUS Client in " + inst.id + ": " + e.getMessage());
        }

        // 1. Version files (vanilla + loader)
        progress.stage(I18n.t("stage.version", inst.mcVersion));
        VersionRepository.ensureVanillaJson(inst.mcVersion, progress);
        String versionId = installedVersionId(inst);
        if (versionId == null) {
            LoaderType type = inst.loaderType();
            Path javaForInstaller = null;
            if (type == LoaderType.FORGE || type == LoaderType.NEOFORGE) {
                ResolvedVersion vanilla = VersionRepository.resolve(inst.mcVersion, progress);
                downloadGameFiles(vanilla, progress, settings.maxDownloads, false);
                javaForInstaller = JavaRuntimes.select(vanilla, inst.javaPath, progress).executable();
            }
            versionId = Loaders.install(type, inst.mcVersion, inst.loaderVersion, javaForInstaller, progress, log);
            inst.versionId = versionId;
            InstanceManager.save(inst);
        }
        progress.checkCancelled();
        ResolvedVersion v = VersionRepository.resolve(versionId, progress);

        // 2. Java
        progress.stage(I18n.t("stage.javaCheck"));
        JavaRuntimes.JavaInstall java = JavaRuntimes.select(v, inst.javaPath, progress);
        progress.checkCancelled();

        // 3. Libraries, client jar, logging config, assets
        downloadGameFiles(v, progress, settings.maxDownloads, true);
        progress.stage(I18n.t("stage.assets"));
        Assets.Index index = Assets.loadIndex(v, progress);
        Downloader.run(Assets.tasks(index), settings.maxDownloads, progress);
        Assets.materializeLegacy(index, inst.gameDir());
        progress.checkCancelled();

        // 4. Natives
        progress.stage(I18n.t("stage.natives"));
        Path nativesDir = LauncherDirs.natives().resolve(v.id());
        Files.createDirectories(nativesDir);
        for (Library lib : v.libraries()) {
            if (!Rule.allows(lib.rules, Map.of())) continue;
            if (lib.natives != null) {
                Path jar = nativeJar(lib);
                if (jar != null && Files.exists(jar)) Natives.extract(jar, nativesDir, lib.extract != null ? lib.extract.exclude : null, false);
            } else if (lib.isNativeOnly()) {
                Path jar = artifactPath(lib);
                if (jar != null && Files.exists(jar)) Natives.extract(jar, nativesDir, lib.extract != null ? lib.extract.exclude : null, true);
            }
        }

        // 5. Game jar: inherited versions get their own copy (what the official launcher does; Forge relies on
        //    the jar being named after ${version_name})
        Path gameJar = LauncherDirs.versionJar(v.baseId());
        if (v.inherited()) {
            Path own = LauncherDirs.versionJar(v.id());
            if (Files.exists(gameJar) && (!Files.exists(own) || Files.size(own) != Files.size(gameJar))) {
                Files.createDirectories(own.getParent());
                Files.copy(gameJar, own, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(own)) gameJar = own;
        }
        if (!Files.exists(gameJar)) throw new IOException(I18n.t("error.clientJar", gameJar));

        // 6. Command line
        progress.stage(I18n.t("stage.launching"));
        List<String> cmd = buildCommand(inst, account, v, java, index, nativesDir, gameJar);
        return new LaunchSpec(cmd, inst.gameDir(), v, java);
    }

    /** Version id to launch if it is already installed, otherwise null. */
    private static String installedVersionId(Instance inst) {
        if (inst.loaderType() == LoaderType.VANILLA) return inst.mcVersion;
        if (inst.versionId != null && VersionRepository.isInstalled(inst.versionId)) return inst.versionId;
        String expected = Loaders.expectedVersionId(inst.loaderType(), inst.mcVersion, inst.loaderVersion);
        return expected != null && VersionRepository.isInstalled(expected) ? expected : null;
    }

    private static void downloadGameFiles(ResolvedVersion v, Progress progress, int threads, boolean includeLibraries) throws IOException {
        progress.stage(I18n.t("stage.libraries"));
        List<Downloader.Task> tasks = new ArrayList<>();
        Path clientJar = LauncherDirs.versionJar(v.baseId());
        if (v.client() != null && v.client().url != null) {
            tasks.add(Downloader.Task.of(v.client().url, clientJar, v.client().sha1, v.client().size));
        } else if (!Files.exists(clientJar)) {
            throw new IOException(I18n.t("error.clientJar", clientJar));
        }
        if (includeLibraries) {
            for (Library lib : v.libraries()) {
                if (!Rule.allows(lib.rules, Map.of())) continue;
                Downloader.Task t = artifactTask(lib);
                if (t != null) tasks.add(t);
                if (lib.natives != null) {
                    Downloader.Task n = nativeTask(lib);
                    if (n != null) tasks.add(n);
                }
            }
            VersionJson.LoggingFile lf = loggingFile(v);
            if (lf != null && lf.url != null) {
                tasks.add(Downloader.Task.of(lf.url, loggingPath(lf), lf.sha1, lf.size));
            }
        }
        // Libraries without a download URL were produced locally by an installer: they must already exist.
        for (Library lib : includeLibraries ? v.libraries() : List.<Library>of()) {
            if (!Rule.allows(lib.rules, Map.of())) continue;
            if (lib.downloads != null && lib.downloads.artifact != null
                    && (lib.downloads.artifact.url == null || lib.downloads.artifact.url.isBlank())) {
                Path p = artifactPath(lib);
                if (p != null && !Files.exists(p)) throw new IOException(I18n.t("error.libraryMissing", lib.name));
            }
        }
        Downloader.run(tasks, threads, progress);
    }

    // ----- library paths -------------------------------------------------------------------------------------

    static Path artifactPath(Library lib) {
        if (lib.downloads != null && lib.downloads.artifact != null) {
            String p = lib.downloads.artifact.path;
            if (p == null || p.isBlank()) p = lib.coordinate().path();
            return LauncherDirs.libraries().resolve(p);
        }
        if (lib.downloads != null && lib.natives != null) return null; // natives-only legacy entry
        return LauncherDirs.libraries().resolve(lib.coordinate().path());
    }

    private static Downloader.Task artifactTask(Library lib) {
        Path path = artifactPath(lib);
        if (path == null) return null;
        if (lib.downloads != null && lib.downloads.artifact != null) {
            VersionJson.Download a = lib.downloads.artifact;
            if (a.url == null || a.url.isBlank()) return null;
            return Downloader.Task.of(a.url, path, a.sha1, a.size);
        }
        if (lib.downloads != null) return null;
        String base = lib.url != null && !lib.url.isBlank() ? lib.url : LIBRARIES_URL;
        if (!base.endsWith("/")) base += "/";
        return Downloader.Task.of(base + lib.coordinate().path(), path, lib.sha1, lib.size);
    }

    private static String nativeClassifier(Library lib) {
        String c = lib.natives.get(OS.ruleName());
        return c == null ? null : c.replace("${arch}", OS.archBits());
    }

    private static Path nativeJar(Library lib) {
        String classifier = nativeClassifier(lib);
        if (classifier == null) return null;
        if (lib.downloads != null && lib.downloads.classifiers != null) {
            VersionJson.Download d = lib.downloads.classifiers.get(classifier);
            if (d == null) return null;
            String p = d.path != null ? d.path : lib.coordinate().withClassifier(classifier).path();
            return LauncherDirs.libraries().resolve(p);
        }
        return LauncherDirs.libraries().resolve(lib.coordinate().withClassifier(classifier).path());
    }

    private static Downloader.Task nativeTask(Library lib) {
        String classifier = nativeClassifier(lib);
        if (classifier == null) return null;
        Path path = nativeJar(lib);
        if (path == null) return null;
        if (lib.downloads != null && lib.downloads.classifiers != null) {
            VersionJson.Download d = lib.downloads.classifiers.get(classifier);
            return d == null || d.url == null ? null : Downloader.Task.of(d.url, path, d.sha1, d.size);
        }
        String base = lib.url != null && !lib.url.isBlank() ? lib.url : LIBRARIES_URL;
        if (!base.endsWith("/")) base += "/";
        return Downloader.Task.of(base + lib.coordinate().withClassifier(classifier).path(), path, null, -1);
    }

    private static VersionJson.LoggingFile loggingFile(ResolvedVersion v) {
        return v.logging() != null && v.logging().client != null ? v.logging().client.file : null;
    }

    private static Path loggingPath(VersionJson.LoggingFile f) {
        return LauncherDirs.assets().resolve("log_configs").resolve(f.id);
    }

    // ----- command line ---------------------------------------------------------------------------------------

    private static List<String> buildCommand(Instance inst, Account account, ResolvedVersion v, JavaRuntimes.JavaInstall java,
                                             Assets.Index index, Path nativesDir, Path gameJar) throws IOException {
        Settings s = Settings.get();
        // Classpath: libraries (allowed by rules, without legacy natives-only entries) then the game jar
        Set<String> cp = new LinkedHashSet<>();
        for (Library lib : v.libraries()) {
            if (!Rule.allows(lib.rules, Map.of())) continue;
            Path p = artifactPath(lib);
            if (p != null && Files.exists(p)) cp.add(p.toString());
        }
        cp.add(gameJar.toString());
        String classpath = String.join(OS.classpathSeparator(), cp);

        Map<String, Boolean> features = new HashMap<>();
        boolean customResolution = inst.width != null && inst.height != null && inst.width > 0 && inst.height > 0;
        features.put("has_custom_resolution", customResolution);
        features.put("is_demo_user", false);
        String[] server = parseServer(inst.autoJoinServer);
        boolean quickPlay = server != null && v.gameArgs().toString().contains("${quickPlayMultiplayer}");
        features.put("is_quick_play_multiplayer", quickPlay);

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", account.name);
        vars.put("version_name", v.id());
        vars.put("game_directory", inst.gameDir().toString());
        vars.put("assets_root", LauncherDirs.assets().toString());
        vars.put("game_assets", index.virtual() ? Assets.virtualDir(index.id()).toString()
                : index.mapToResources() ? inst.gameDir().resolve("resources").toString() : LauncherDirs.assets().toString());
        vars.put("assets_index_name", v.assetsId());
        vars.put("auth_uuid", account.uuidNoDashes());
        String token = account.isMicrosoft() && account.mcAccessToken != null ? account.mcAccessToken : "0";
        vars.put("auth_access_token", token);
        vars.put("auth_session", account.isMicrosoft() ? "token:" + token + ":" + account.uuidNoDashes() : "-");
        vars.put("clientid", "");
        vars.put("auth_xuid", "");
        vars.put("user_type", account.isMicrosoft() ? "msa" : "legacy");
        vars.put("user_properties", "{}");
        vars.put("version_type", v.type() != null ? v.type() : "release");
        vars.put("resolution_width", customResolution ? String.valueOf(inst.width) : "854");
        vars.put("resolution_height", customResolution ? String.valueOf(inst.height) : "480");
        vars.put("natives_directory", nativesDir.toString());
        vars.put("launcher_name", "nexus-launcher");
        vars.put("launcher_version", BuildInfo.VERSION);
        vars.put("classpath", classpath);
        vars.put("classpath_separator", OS.classpathSeparator());
        vars.put("library_directory", LauncherDirs.libraries().toString());
        if (quickPlay) vars.put("quickPlayMultiplayer", server[0] + ":" + server[1]);

        List<String> cmd = new ArrayList<>();
        cmd.add(java.executable().toString());

        int min = inst.minMemoryMb != null ? inst.minMemoryMb : s.minMemoryMb;
        int max = inst.maxMemoryMb != null ? inst.maxMemoryMb : s.maxMemoryMb;
        cmd.add("-Xms" + Math.min(min, max) + "M");
        cmd.add("-Xmx" + max + "M");
        String customJvm = inst.jvmArgs != null && !inst.jvmArgs.isBlank() ? inst.jvmArgs : s.jvmArgs;
        List<String> userJvm = splitArgs(customJvm);
        // G1 flags are meaningless (and some unknown) on Java 8 without the experimental unlock: keep them anyway,
        // the JVM accepts all of the default ones since Java 8u40.
        cmd.addAll(userJvm);

        if (v.modernJvm()) {
            for (String a : evaluate(v.jvmArgs(), features)) cmd.add(substitute(a, vars));
        } else {
            if (OS.isMac()) cmd.add("-XstartOnFirstThread");
            if (OS.isWindows()) cmd.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
            if (OS.ARCH.equals("x86")) cmd.add("-Xss1M");
            cmd.add("-Djava.library.path=" + nativesDir);
            cmd.add("-Dminecraft.launcher.brand=nexus-launcher");
            cmd.add("-Dminecraft.launcher.version=" + BuildInfo.VERSION);
            // Loader profiles on top of legacy versions may still bring their own JVM flags
            for (String a : evaluate(v.jvmArgs(), features)) cmd.add(substitute(a, vars));
            cmd.add("-cp");
            cmd.add(classpath);
        }

        VersionJson.LoggingFile lf = loggingFile(v);
        if (lf != null && v.logging().client.argument != null && Files.exists(loggingPath(lf))) {
            cmd.add(v.logging().client.argument.replace("${path}", loggingPath(lf).toString()));
        }

        cmd.add(v.mainClass());

        List<String> game = new ArrayList<>();
        if (v.legacyArgs() != null) {
            for (String a : v.legacyArgs().trim().split("\\s+")) if (!a.isEmpty()) game.add(substitute(a, vars));
        }
        for (String a : evaluate(v.gameArgs(), features)) game.add(substitute(a, vars));
        if (v.legacyArgs() != null && customResolution && !game.contains("--width")) {
            game.addAll(List.of("--width", String.valueOf(inst.width), "--height", String.valueOf(inst.height)));
        }
        if (inst.fullscreen && !game.contains("--fullscreen")) game.add("--fullscreen");
        if (server != null && !quickPlay) game.addAll(List.of("--server", server[0], "--port", server[1]));
        cmd.addAll(game);

        return maybeArgFile(cmd, java, inst);
    }

    /** Evaluates the modern argument list: plain strings and {rules, value} objects. */
    private static List<String> evaluate(List<JsonElement> args, Map<String, Boolean> features) {
        List<String> out = new ArrayList<>();
        if (args == null) return out;
        for (JsonElement e : args) {
            if (e.isJsonPrimitive()) {
                out.add(e.getAsString());
            } else if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                List<Rule> rules = o.has("rules") ? Json.GSON.fromJson(o.get("rules"), new TypeToken<List<Rule>>() {}.getType()) : null;
                if (!Rule.allows(rules, features)) continue;
                JsonElement value = o.get("value");
                if (value == null) continue;
                if (value.isJsonArray()) value.getAsJsonArray().forEach(x -> out.add(x.getAsString()));
                else out.add(value.getAsString());
            }
        }
        return out;
    }

    private static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z_]+)}");

    private static String substitute(String arg, Map<String, String> vars) {
        Matcher m = VAR.matcher(arg);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Splits a user-entered argument string, honouring "double" and 'single' quotes. */
    public static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else cur.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                any = true;
            } else if (Character.isWhitespace(c)) {
                if (cur.length() > 0 || any) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    any = false;
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || any) out.add(cur.toString());
        return out;
    }

    /** "host", "host:port", "[v6]:port" -> {host, port} */
    static String[] parseServer(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        String host = t, port = "25565";
        if (t.startsWith("[")) {
            int end = t.indexOf(']');
            if (end > 0) {
                host = t.substring(1, end);
                if (t.length() > end + 2 && t.charAt(end + 1) == ':') port = t.substring(end + 2);
            }
        } else {
            int colon = t.lastIndexOf(':');
            if (colon > 0 && t.indexOf(':') == colon) {
                host = t.substring(0, colon);
                port = t.substring(colon + 1);
            }
        }
        if (!port.matches("\\d{1,5}")) port = "25565";
        return new String[]{host, port};
    }

    /**
     * Windows limits a command line to 32 767 characters; long modded classpaths can exceed it. Java 9+ reads
     * arguments from an @file, so everything after the executable goes there when needed.
     */
    private static List<String> maybeArgFile(List<String> cmd, JavaRuntimes.JavaInstall java, Instance inst) throws IOException {
        int length = 0;
        for (String a : cmd) length += a.length() + 3;
        if (!OS.isWindows() || length < 30000 || (java.major() > 0 && java.major() < 9)) return cmd;
        Path file = inst.dir.resolve("launch-args.txt");
        StringBuilder sb = new StringBuilder();
        for (String a : cmd.subList(1, cmd.size())) {
            sb.append('"').append(a.replace("\\", "\\\\").replace("\"", "\\\"")).append('"').append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        Log.info("Command line is " + length + " characters long, using an argument file");
        return List.of(cmd.get(0), "@" + file);
    }
}
