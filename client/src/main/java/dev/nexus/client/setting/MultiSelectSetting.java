package dev.nexus.client.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Any subset of a fixed list of options. The stored set is immutable and keeps option order. */
public final class MultiSelectSetting extends Setting<Set<String>> {
    private List<String> options;

    public MultiSelectSetting(String id, String name, String description, List<String> options, String... defaults) {
        super(id, name, description, Set.of(defaults));
        this.options = List.copyOf(options);
        revalidateDefault();
    }

    @Override
    protected Set<String> validate(Set<String> candidate) {
        if (candidate == null) return null;
        if (options == null) return candidate; // during super()
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String o : options) if (candidate.contains(o)) out.add(o);
        return Collections.unmodifiableSet(out);
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean isSelected(String option) {
        return value.contains(option);
    }

    public void toggle(String option) {
        LinkedHashSet<String> next = new LinkedHashSet<>(value);
        if (!next.remove(option)) next.add(option);
        set(next);
    }

    @Override
    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (String s : value) arr.add(s);
        return arr;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonArray()) return;
        LinkedHashSet<String> read = new LinkedHashSet<>();
        for (JsonElement e : json.getAsJsonArray()) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) read.add(e.getAsString());
        }
        set(read);
    }
}
