package dev.nexus.launcher.game;

import java.util.List;
import java.util.Map;

/** A library entry of a version file. Supports Mojang's format and the plain Maven style used by loaders. */
public final class Library {
    public String name;
    /** Maven repository base URL (Fabric/Quilt style, when {@link #downloads} is absent). */
    public String url;
    public String sha1;
    public long size = -1;
    public Downloads downloads;
    /** Legacy natives: OS name to classifier, e.g. "windows" -> "natives-windows-${arch}". */
    public Map<String, String> natives;
    public List<Rule> rules;
    public Extract extract;

    public static final class Downloads {
        public VersionJson.Download artifact;
        public Map<String, VersionJson.Download> classifiers;
    }

    public static final class Extract {
        public List<String> exclude;
    }

    /** Parsed Maven coordinate "group:artifact:version[:classifier][@extension]". */
    public record Coordinate(String group, String artifact, String version, String classifier, String extension) {
        public static Coordinate parse(String name) {
            String ext = "jar";
            String n = name;
            int at = n.indexOf('@');
            if (at >= 0) {
                ext = n.substring(at + 1);
                n = n.substring(0, at);
            }
            String[] p = n.split(":");
            if (p.length < 3) throw new IllegalArgumentException("Invalid library name: " + name);
            return new Coordinate(p[0], p[1], p[2], p.length > 3 ? p[3] : null, ext);
        }

        public Coordinate withClassifier(String c) {
            return new Coordinate(group, artifact, version, c, extension);
        }

        /** Relative repository path, e.g. org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar */
        public String path() {
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version
                    + (classifier != null ? "-" + classifier : "") + "." + extension;
        }

        /** Key used to de-duplicate libraries between a loader profile and its parent version. */
        public String dedupeKey() {
            return group + ":" + artifact + (classifier != null ? ":" + classifier : "");
        }
    }

    public Coordinate coordinate() {
        return Coordinate.parse(name);
    }

    public boolean isNativeOnly() {
        return name != null && name.contains(":natives-");
    }
}
