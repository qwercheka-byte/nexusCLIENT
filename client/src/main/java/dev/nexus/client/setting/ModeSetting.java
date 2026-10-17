package dev.nexus.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** Selects exactly one of several named modes. */
public final class ModeSetting extends Setting<String> {
    private List<String> modes;

    public ModeSetting(String id, String name, String description, String defaultValue, String... modes) {
        super(id, name, description, init(defaultValue, modes));
        this.modes = List.of(modes);
    }

    private static String init(String def, String[] modes) {
        if (modes.length == 0) throw new IllegalArgumentException("ModeSetting needs at least one mode");
        for (String m : modes) if (m.equals(def)) return def;
        throw new IllegalArgumentException("Default mode '" + def + "' is not one of the modes");
    }

    @Override
    protected String validate(String candidate) {
        if (modes == null) return candidate; // super-constructor call, already checked by init()
        for (String m : modes) if (m.equalsIgnoreCase(candidate)) return m;
        return null;
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public int index() {
        return modes.indexOf(value);
    }

    public void cycle(int direction) {
        int i = Math.floorMod(index() + direction, modes.size());
        set(modes.get(i));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) set(json.getAsString());
    }
}
