package dev.nexus.launcher.game;

public enum LoaderType {
    VANILLA("Vanilla", null),
    FABRIC("Fabric", "fabric"),
    QUILT("Quilt", "quilt"),
    FORGE("Forge", "forge"),
    NEOFORGE("NeoForge", "neoforge");

    private final String display;
    private final String modrinthId;

    LoaderType(String display, String modrinthId) {
        this.display = display;
        this.modrinthId = modrinthId;
    }

    public String display() {
        return display;
    }

    /** Loader name used by Modrinth facets/version filters, null for vanilla. */
    public String modrinthId() {
        return modrinthId;
    }

    public boolean supportsMods() {
        return this != VANILLA;
    }

    public static LoaderType parse(String s) {
        if (s == null) return VANILLA;
        try {
            return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VANILLA;
        }
    }
}
