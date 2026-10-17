package dev.nexus.launcher.instance;

import dev.nexus.launcher.core.FileUtil;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.game.LoaderType;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Loads, creates, saves and removes instances. All methods may block on disk I/O: call them off the FX thread. */
public final class InstanceManager {
    public static final Set<String> COLORS = Set.of("violet", "cyan", "emerald", "rose", "amber", "slate");
    private static final List<Instance> instances = new CopyOnWriteArrayList<>();

    private InstanceManager() {}

    public static synchronized List<Instance> reload() {
        List<Instance> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(LauncherDirs.instances())) {
            for (Path dir : ds) {
                Path cfg = dir.resolve("instance.json");
                if (!Files.isRegularFile(cfg)) continue;
                try {
                    Instance inst = Json.read(cfg, Instance.class);
                    if (inst == null || inst.mcVersion == null) throw new IOException("missing mcVersion");
                    inst.id = dir.getFileName().toString();
                    inst.dir = dir;
                    if (inst.name == null || inst.name.isBlank()) inst.name = inst.id;
                    if (inst.modrinth == null) inst.modrinth = new java.util.LinkedHashMap<>();
                    if (inst.color == null || !COLORS.contains(inst.color)) inst.color = "violet";
                    list.add(inst);
                } catch (IOException | RuntimeException e) {
                    Log.warn("Skipping broken instance " + dir.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.error("Cannot list instances", e);
        }
        list.sort(Comparator.comparingLong((Instance i) -> -Math.max(i.lastPlayed, i.created)));
        instances.clear();
        instances.addAll(list);
        return List.copyOf(list);
    }

    public static List<Instance> all() {
        return List.copyOf(instances);
    }

    public static Instance find(String id) {
        if (id == null) return null;
        for (Instance i : instances) if (i.id.equals(id)) return i;
        return null;
    }

    public static synchronized Instance create(String name, String mcVersion, LoaderType loader, String loaderVersion, String color) throws IOException {
        String base = FileUtil.safeName(name);
        String id = base;
        int n = 2;
        while (Files.exists(LauncherDirs.instances().resolve(id))) id = base + " (" + n++ + ")";
        Path dir = LauncherDirs.instances().resolve(id);
        Files.createDirectories(dir.resolve("minecraft").resolve("mods"));
        Instance inst = new Instance();
        inst.id = id;
        inst.dir = dir;
        inst.name = name.trim();
        inst.mcVersion = mcVersion;
        inst.loader = loader.name();
        inst.loaderVersion = loader == LoaderType.VANILLA ? null : loaderVersion;
        inst.color = color != null && COLORS.contains(color) ? color : "violet";
        inst.created = System.currentTimeMillis();
        save(inst);
        instances.add(0, inst);
        Log.info("Created instance " + id + " (" + inst.describe() + ")");
        return inst;
    }

    public static synchronized void save(Instance inst) throws IOException {
        Json.write(inst.configFile(), inst);
    }

    public static synchronized void delete(Instance inst) throws IOException {
        instances.remove(inst);
        FileUtil.deleteRecursively(inst.dir);
        Log.info("Deleted instance " + inst.id);
    }

    public static synchronized Instance duplicate(Instance src, String newName) throws IOException {
        String base = FileUtil.safeName(newName);
        String id = base;
        int n = 2;
        while (Files.exists(LauncherDirs.instances().resolve(id))) id = base + " (" + n++ + ")";
        Path dir = LauncherDirs.instances().resolve(id);
        FileUtil.copyRecursively(src.dir, dir);
        Instance copy = Json.read(dir.resolve("instance.json"), Instance.class);
        copy.id = id;
        copy.dir = dir;
        copy.name = newName.trim();
        copy.created = System.currentTimeMillis();
        copy.lastPlayed = 0;
        copy.playtimeSeconds = 0;
        save(copy);
        instances.add(0, copy);
        return copy;
    }
}
