package dev.nexus.client.waypoint;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent storage of player waypoints: JSON file in the game directory (nexus/waypoints.json),
 * files only, never sent to any server.
 */
public final class WaypointStore {
    public static final WaypointStore INSTANCE = new WaypointStore();

    private static final int MAX = 64;
    private static final Gson GSON = new Gson();

    private final List<Waypoint> waypoints = new ArrayList<>();
    private boolean loaded;

    private WaypointStore() {}

    public List<Waypoint> all() {
        ensureLoaded();
        return Collections.unmodifiableList(waypoints);
    }

    public List<Waypoint> inDimension(String dim) {
        ensureLoaded();
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint wp : waypoints) if (wp.dim.equals(dim)) out.add(wp);
        return out;
    }

    public void add(Waypoint wp) {
        ensureLoaded();
        if (waypoints.size() >= MAX) {
            waypoints.remove(0); // oldest drops off, hard cap keeps HUD and file bounded
        }
        waypoints.add(wp);
        save();
    }

    public boolean removeAt(int index) {
        ensureLoaded();
        if (index < 0 || index >= waypoints.size()) return false;
        waypoints.remove(index);
        save();
        return true;
    }

    public boolean removeByName(String name) {
        ensureLoaded();
        boolean removed = waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    public boolean rename(String oldName, String newName) {
        ensureLoaded();
        for (Waypoint w : waypoints) {
            if (w.name.equalsIgnoreCase(oldName)) {
                w.name = newName;
                save();
                return true;
            }
        }
        return false;
    }

    public int clear(String dim) {
        ensureLoaded();
        int before = waypoints.size();
        waypoints.removeIf(w -> w.dim.equals(dim));
        int removed = before - waypoints.size();
        if (removed > 0) save();
        return removed;
    }

    // ------------------------------------------------------------------------------------------------ persistence

    private File file() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "nexus");
        return new File(dir, "waypoints.json");
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.isFile()) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || !root.has("waypoints")) return;
            JsonArray arr = root.getAsJsonArray("waypoints");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                waypoints.add(new Waypoint(
                        o.get("name").getAsString(),
                        o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                        o.get("dim").getAsString(),
                        o.get("color").getAsInt()));
            }
        } catch (IOException | RuntimeException e) {
            // corrupted file => start empty, next save overwrites it
        }
    }

    private void save() {
        try {
            JsonArray arr = new JsonArray();
            for (Waypoint w : waypoints) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name);
                o.addProperty("x", w.x);
                o.addProperty("y", w.y);
                o.addProperty("z", w.z);
                o.addProperty("dim", w.dim);
                o.addProperty("color", w.color);
                arr.add(o);
            }
            JsonObject root = new JsonObject();
            root.add("waypoints", arr);
            File f = file();
            Files.createDirectories(f.toPath().getParent());
            Files.writeString(f.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }
}
