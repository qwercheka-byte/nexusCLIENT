package dev.nexus.launcher.instance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lists and manages the mods folder of an instance. Disabled mods are renamed to *.jar.disabled. */
public final class ModManager {
    private static final String DISABLED = ".disabled";

    /** Metadata of one mod file. {@code icon} holds PNG bytes or null. */
    public record ModFile(Path file, String fileName, boolean enabled, String id, String name, String version,
                          String description, String authors, byte[] icon, long size) {
        public String displayName() {
            return name != null && !name.isBlank() ? name : fileName;
        }

        /** File name without the .disabled suffix. */
        public String baseName() {
            return enabled ? fileName : fileName.substring(0, fileName.length() - DISABLED.length());
        }
    }

    private ModManager() {}

    public static List<ModFile> list(Instance inst) {
        List<ModFile> out = new ArrayList<>();
        Path dir = inst.modsDir();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                String lower = n.toLowerCase(Locale.ROOT);
                boolean enabled = lower.endsWith(".jar") || lower.endsWith(".zip");
                boolean disabled = lower.endsWith(".jar" + DISABLED) || lower.endsWith(".zip" + DISABLED);
                if (!Files.isRegularFile(p) || (!enabled && !disabled)) continue;
                out.add(read(p, enabled));
            }
        } catch (IOException e) {
            Log.warn("Cannot list mods of " + inst.id + ": " + e.getMessage());
        }
        out.sort(Comparator.comparing((ModFile m) -> m.displayName().toLowerCase(Locale.ROOT)));
        return out;
    }

    public static Path setEnabled(ModFile mod, boolean enabled) throws IOException {
        if (mod.enabled() == enabled) return mod.file();
        Path target = mod.file().resolveSibling(enabled ? mod.baseName() : mod.fileName() + DISABLED);
        return Files.move(mod.file(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void delete(Instance inst, ModFile mod) throws IOException {
        Files.deleteIfExists(mod.file());
        inst.modrinth.remove(mod.baseName());
    }

    public static Path addFile(Instance inst, Path source) throws IOException {
        Files.createDirectories(inst.modsDir());
        Path target = inst.modsDir().resolve(source.getFileName().toString());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static ModFile read(Path file, boolean enabled) {
        String fileName = file.getFileName().toString();
        long size = 0;
        try {
            size = Files.size(file);
        } catch (IOException ignored) {
            // keep 0
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ModFile m = readFabric(zip, file, fileName, enabled, size);
            if (m == null) m = readQuilt(zip, file, fileName, enabled, size);
            if (m == null) m = readToml(zip, file, fileName, enabled, size);
            if (m == null) m = readMcmod(zip, file, fileName, enabled, size);
            if (m != null) return m;
        } catch (IOException | RuntimeException e) {
            Log.warn("Cannot read mod metadata of " + fileName + ": " + e.getMessage());
        }
        return new ModFile(file, fileName, enabled, null, null, null, null, null, null, size);
    }

    private static ModFile readFabric(ZipFile zip, Path file, String fileName, boolean enabled, long size) throws IOException {
        JsonObject o = jsonEntry(zip, "fabric.mod.json");
        if (o == null) return null;
        String icon = null;
        if (o.has("icon")) {
            JsonElement i = o.get("icon");
            if (i.isJsonPrimitive()) icon = i.getAsString();
            else if (i.isJsonObject()) {
                // {"16": "...", "128": "..."}: take the largest
                int best = -1;
                for (var e : i.getAsJsonObject().entrySet()) {
                    try {
                        int s = Integer.parseInt(e.getKey());
                        if (s > best) {
                            best = s;
                            icon = e.getValue().getAsString();
                        }
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
            }
        }
        return new ModFile(file, fileName, enabled, Json.str(o, "id", null), Json.str(o, "name", null),
                Json.str(o, "version", null), Json.str(o, "description", null), people(o.get("authors")),
                bytes(zip, icon), size);
    }

    private static ModFile readQuilt(ZipFile zip, Path file, String fileName, boolean enabled, long size) throws IOException {
        JsonObject o = jsonEntry(zip, "quilt.mod.json");
        if (o == null || !o.has("quilt_loader")) return null;
        JsonObject ql = o.getAsJsonObject("quilt_loader");
        JsonObject meta = ql.has("metadata") ? ql.getAsJsonObject("metadata") : new JsonObject();
        String icon = meta.has("icon") && meta.get("icon").isJsonPrimitive() ? meta.get("icon").getAsString() : null;
        String authors = null;
        if (meta.has("contributors") && meta.get("contributors").isJsonObject()) {
            authors = String.join(", ", meta.getAsJsonObject("contributors").keySet());
        }
        return new ModFile(file, fileName, enabled, Json.str(ql, "id", null), Json.str(meta, "name", null),
                Json.str(ql, "version", null), Json.str(meta, "description", null), authors, bytes(zip, icon), size);
    }

    private static final Pattern TOML_KV = Pattern.compile("^\\s*([A-Za-z]+)\\s*=\\s*(?:\"\"\"([\\s\\S]*?)\"\"\"|'''([\\s\\S]*?)'''|\"((?:[^\"\\\\]|\\\\.)*)\"|'([^']*)')", Pattern.MULTILINE);

    /** Minimal reader for the first [[mods]] table of (neoforge.)mods.toml. */
    private static ModFile readToml(ZipFile zip, Path file, String fileName, boolean enabled, long size) throws IOException {
        String toml = textEntry(zip, "META-INF/neoforge.mods.toml");
        if (toml == null) toml = textEntry(zip, "META-INF/mods.toml");
        if (toml == null) return null;
        int start = toml.indexOf("[[mods]]");
        String section = start >= 0 ? toml.substring(start + 8) : toml;
        int next = section.indexOf("\n[");
        if (next >= 0) section = section.substring(0, next);
        String id = null, name = null, version = null, description = null, authors = null, logo = null;
        Matcher m = TOML_KV.matcher(section);
        while (m.find()) {
            String v = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(5);
            switch (m.group(1)) {
                case "modId" -> id = v;
                case "displayName" -> name = v;
                case "version" -> version = v;
                case "description" -> description = v.trim();
                case "authors" -> authors = v;
                case "logoFile" -> logo = v;
                default -> { }
            }
        }
        // Top-level keys (outside [[mods]]) may hold the logo/authors as well
        if (logo == null) {
            Matcher top = TOML_KV.matcher(start >= 0 ? toml.substring(0, start) : "");
            while (top.find()) if (top.group(1).equals("logoFile")) logo = top.group(4) != null ? top.group(4) : top.group(5);
        }
        if (version != null && version.contains("${file.jarVersion}")) {
            String implVersion = manifestVersion(zip);
            version = implVersion != null ? version.replace("${file.jarVersion}", implVersion) : null;
        }
        return new ModFile(file, fileName, enabled, id, name, version, description, authors, bytes(zip, logo), size);
    }

    private static ModFile readMcmod(ZipFile zip, Path file, String fileName, boolean enabled, long size) throws IOException {
        String text = textEntry(zip, "mcmod.info");
        if (text == null) return null;
        try {
            JsonElement e = JsonParser.parseString(text);
            JsonArray arr = e.isJsonArray() ? e.getAsJsonArray()
                    : e.isJsonObject() && e.getAsJsonObject().has("modList") ? e.getAsJsonObject().getAsJsonArray("modList") : null;
            if (arr == null || arr.isEmpty()) return null;
            JsonObject o = arr.get(0).getAsJsonObject();
            return new ModFile(file, fileName, enabled, Json.str(o, "modid", null), Json.str(o, "name", null),
                    Json.str(o, "version", null), Json.str(o, "description", null), people(o.get("authorList")),
                    bytes(zip, Json.str(o, "logoFile", null)), size);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String people(JsonElement e) {
        if (e == null || !e.isJsonArray()) return null;
        List<String> names = new ArrayList<>();
        for (JsonElement a : e.getAsJsonArray()) {
            if (a.isJsonPrimitive()) names.add(a.getAsString());
            else if (a.isJsonObject() && a.getAsJsonObject().has("name")) names.add(a.getAsJsonObject().get("name").getAsString());
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private static String manifestVersion(ZipFile zip) throws IOException {
        ZipEntry e = zip.getEntry("META-INF/MANIFEST.MF");
        if (e == null) return null;
        try (InputStream in = zip.getInputStream(e)) {
            return new Manifest(in).getMainAttributes().getValue("Implementation-Version");
        }
    }

    private static JsonObject jsonEntry(ZipFile zip, String name) throws IOException {
        String text = textEntry(zip, name);
        if (text == null) return null;
        try {
            JsonElement e = JsonParser.parseString(text);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String textEntry(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null) return null;
        try (InputStream in = zip.getInputStream(e)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] bytes(ZipFile zip, String path) throws IOException {
        if (path == null || path.isBlank()) return null;
        String p = path.startsWith("/") ? path.substring(1) : path;
        ZipEntry e = zip.getEntry(p);
        if (e == null || e.getSize() > 2 * 1024 * 1024) return null;
        try (InputStream in = zip.getInputStream(e)) {
            return in.readAllBytes();
        }
    }
}
