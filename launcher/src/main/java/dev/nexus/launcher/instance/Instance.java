package dev.nexus.launcher.instance;

import dev.nexus.launcher.game.LoaderType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** One isolated game installation: its own game folder (saves, mods, options) on top of shared game files. */
public final class Instance {
    public static final int SCHEMA = 1;

    /** Folder name inside instances/, not serialised. */
    public transient String id;
    public transient Path dir;

    public int schema = SCHEMA;
    public String name;
    public String mcVersion;
    public String loader = LoaderType.VANILLA.name();
    public String loaderVersion;
    /** Id of the version file this instance launches (set once the loader is installed). */
    public String versionId;
    public String color = "violet";
    public long created;
    public long lastPlayed;
    public long playtimeSeconds;

    // Per-instance overrides; null/blank = launcher default
    public Integer minMemoryMb;
    public Integer maxMemoryMb;
    public String javaPath;
    public String jvmArgs;
    public Integer width;
    public Integer height;
    public boolean fullscreen;
    /** host[:port] to join right after start. */
    public String autoJoinServer;

    /** Files installed from Modrinth: file name to origin (used for "installed" badges and updates). */
    public Map<String, ModrinthRef> modrinth = new LinkedHashMap<>();

    public static final class ModrinthRef {
        public String projectId;
        public String versionId;
        public String title;

        public ModrinthRef() {}

        public ModrinthRef(String projectId, String versionId, String title) {
            this.projectId = projectId;
            this.versionId = versionId;
            this.title = title;
        }
    }

    public LoaderType loaderType() {
        return LoaderType.parse(loader);
    }

    public Path gameDir() {
        return dir.resolve("minecraft");
    }

    public Path modsDir() {
        return gameDir().resolve("mods");
    }

    public Path configFile() {
        return dir.resolve("instance.json");
    }

    /** "1.21.11 · Fabric 0.17.2" */
    public String describe() {
        LoaderType t = loaderType();
        if (t == LoaderType.VANILLA) return mcVersion;
        String lv = loaderVersion == null ? "" : " " + shortLoaderVersion();
        return mcVersion + " · " + t.display() + lv;
    }

    /** Forge versions are stored as "1.21.11-61.0.2"; show only the loader part. */
    public String shortLoaderVersion() {
        if (loaderVersion == null) return "";
        return loaderVersion.startsWith(mcVersion + "-") ? loaderVersion.substring(mcVersion.length() + 1) : loaderVersion;
    }
}
