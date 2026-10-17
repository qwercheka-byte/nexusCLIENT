package dev.nexus.launcher.game;

import java.util.List;

/** piston-meta version_manifest_v2.json */
public final class VersionManifest {
    public Latest latest;
    public List<Entry> versions;

    public static final class Latest {
        public String release;
        public String snapshot;
    }

    public static final class Entry {
        public String id;
        /** release, snapshot, old_beta, old_alpha */
        public String type;
        public String url;
        public String time;
        public String releaseTime;
        public String sha1;

        public boolean isRelease() { return "release".equals(type); }

        public boolean isSnapshot() { return "snapshot".equals(type); }

        public boolean isOld() { return type != null && type.startsWith("old_"); }
    }

    public Entry find(String id) {
        if (versions == null) return null;
        for (Entry e : versions) if (e.id.equals(id)) return e;
        return null;
    }
}
