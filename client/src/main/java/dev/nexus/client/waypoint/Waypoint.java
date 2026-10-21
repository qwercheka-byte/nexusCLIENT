package dev.nexus.client.waypoint;

/** A single player-placed marker in the world. */
public final class Waypoint {
    public String name;
    public final double x, y, z;
    /** Registry id of the dimension, e.g. "minecraft:overworld". */
    public final String dim;
    /** 0xRRGGBB */
    public final int color;
    public final long createdAt;

    public Waypoint(String name, double x, double y, double z, String dim, int color) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.color = color;
        this.createdAt = System.currentTimeMillis();
    }

    public double distanceTo(double px, double pz) {
        double dx = this.x - px, dz = this.z - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
