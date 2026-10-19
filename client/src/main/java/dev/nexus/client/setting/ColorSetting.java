package dev.nexus.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nexus.client.util.ColorUtil;

/** An ARGB colour. When alpha editing is disabled the alpha channel is forced to 255. */
public final class ColorSetting extends Setting<Integer> {
    private final boolean allowAlpha;
    private final boolean initialized;

    public ColorSetting(String id, String name, String description, int defaultValue, boolean allowAlpha) {
        super(id, name, description, allowAlpha ? defaultValue : (defaultValue | 0xFF000000));
        this.allowAlpha = allowAlpha;
        this.initialized = true;
    }

    @Override
    protected Integer validate(Integer candidate) {
        if (candidate == null) return null;
        // fields are unset during super(); the constructor already passes a corrected value
        if (!initialized) return candidate;
        return allowAlpha || ColorUtil.alpha(candidate) == 0xFF ? candidate : (candidate | 0xFF000000);
    }

    public boolean allowsAlpha() {
        return allowAlpha;
    }

    public int getColor() {
        return value;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(ColorUtil.toHex(value, true));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return;
        if (json.getAsJsonPrimitive().isString()) {
            Integer parsed = ColorUtil.parseHex(json.getAsString());
            if (parsed != null) set(parsed);
        } else if (json.getAsJsonPrimitive().isNumber()) {
            set((int) json.getAsLong());
        }
    }
}
