package dev.nexus.launcher.game;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

/**
 * A version file as published by Mojang ({@code versions/<id>/<id>.json}) or produced by a mod loader
 * (which usually only lists its differences and points to the vanilla version with {@code inheritsFrom}).
 * All fields are optional; Gson fills what is present.
 */
public final class VersionJson {
    public String id;
    public String inheritsFrom;
    public String type;
    public String mainClass;
    /** Pre-1.13 argument string. */
    public String minecraftArguments;
    public Arguments arguments;
    public List<Library> libraries;
    public String assets;
    public AssetIndexRef assetIndex;
    public Map<String, Download> downloads;
    public JavaVersion javaVersion;
    public Logging logging;
    public String releaseTime;
    /** Some loader profiles name the vanilla jar explicitly. */
    public String jar;

    public static final class Arguments {
        public List<JsonElement> game;
        public List<JsonElement> jvm;
    }

    public static final class AssetIndexRef {
        public String id;
        public String sha1;
        public long size;
        public long totalSize;
        public String url;
    }

    public static final class Download {
        public String path;
        public String sha1;
        public long size = -1;
        public String url;
    }

    public static final class JavaVersion {
        public String component;
        public int majorVersion;
    }

    public static final class Logging {
        public LoggingEntry client;
    }

    public static final class LoggingEntry {
        public String argument;
        public String type;
        public LoggingFile file;
    }

    public static final class LoggingFile {
        public String id;
        public String sha1;
        public long size = -1;
        public String url;
    }
}
