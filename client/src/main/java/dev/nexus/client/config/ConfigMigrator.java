package dev.nexus.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.client.NexusClient;

import java.util.Map;

/**
 * Upgrades configuration JSON written by older versions to the current schema.
 * <p>
 * Each step converts version N to N+1 in place. To change the format later: bump
 * {@link #CURRENT}, add a step and keep the old ones. The original file is backed up by the caller.
 */
public final class ConfigMigrator {
    public static final int CURRENT = 2;

    private ConfigMigrator() {}

    public static int schemaOf(JsonObject json) {
        JsonElement v = json.get("schema");
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsInt() : 1;
    }

    /** @return true if the profile JSON was changed */
    public static boolean migrateProfile(JsonObject json) {
        int schema = schemaOf(json);
        if (schema > CURRENT) {
            NexusClient.LOGGER.warn("Profile schema {} is newer than supported {}; loading best effort", schema, CURRENT);
            return false;
        }
        boolean changed = false;
        if (schema < 2) {
            profileV1toV2(json);
            changed = true;
        }
        json.addProperty("schema", CURRENT);
        return changed;
    }

    /** @return true if the client JSON was changed */
    public static boolean migrateClient(JsonObject json) {
        int schema = schemaOf(json);
        if (schema > CURRENT) {
            NexusClient.LOGGER.warn("Client config schema {} is newer than supported {}", schema, CURRENT);
            return false;
        }
        boolean changed = false;
        if (schema < 2) {
            clientV1toV2(json);
            changed = true;
        }
        json.addProperty("schema", CURRENT);
        return changed;
    }

    /**
     * v1: modules were stored at the root, the hotkey was named "keybind" and HUD positions were absolute
     * {@code {"x":..,"y":..}} from the top-left corner.
     * v2: modules live under "modules", the hotkey is "key", HUD positions are anchored.
     */
    private static void profileV1toV2(JsonObject json) {
        JsonObject modules;
        if (json.has("modules") && json.get("modules").isJsonObject()) {
            modules = json.getAsJsonObject("modules");
        } else {
            modules = new JsonObject();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                if (e.getValue().isJsonObject()) modules.add(e.getKey(), e.getValue());
            }
            for (String key : modules.keySet()) json.remove(key);
            json.add("modules", modules);
        }
        for (Map.Entry<String, JsonElement> e : modules.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject m = e.getValue().getAsJsonObject();
            if (m.has("keybind") && !m.has("key")) m.add("key", m.remove("keybind"));
            JsonElement hud = m.get("hud");
            if (hud != null && hud.isJsonObject()) {
                JsonObject h = hud.getAsJsonObject();
                if (h.has("x") && h.has("y") && !h.has("ax")) {
                    JsonObject converted = new JsonObject();
                    converted.addProperty("ax", 0);
                    converted.addProperty("ay", 0);
                    converted.add("ox", h.get("x"));
                    converted.add("oy", h.get("y"));
                    if (h.has("scale")) converted.add("scale", h.get("scale"));
                    m.add("hud", converted);
                }
            }
        }
    }

    /** v1 stored the accent as {"theme":{"accent":"#RRGGBB"}}; v2 uses the accent preset settings. */
    private static void clientV1toV2(JsonObject json) {
        JsonObject client = json.has("client") && json.get("client").isJsonObject() ? json.getAsJsonObject("client") : new JsonObject();
        JsonElement theme = json.remove("theme");
        if (theme != null && theme.isJsonObject() && theme.getAsJsonObject().has("accent")) {
            client.addProperty("accent_preset", "Custom");
            client.add("custom_accent", theme.getAsJsonObject().get("accent"));
        }
        json.add("client", client);
    }
}
