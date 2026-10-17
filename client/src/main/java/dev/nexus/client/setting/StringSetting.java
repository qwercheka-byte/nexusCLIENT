package dev.nexus.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Predicate;

/** Free text with a maximum length and an optional validator. */
public final class StringSetting extends Setting<String> {
    private int maxLength = 64;
    private Predicate<String> validator = s -> true;
    private String placeholder = "";

    public StringSetting(String id, String name, String description, String defaultValue) {
        super(id, name, description, defaultValue);
    }

    public StringSetting maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public StringSetting validator(Predicate<String> validator) {
        this.validator = validator;
        return this;
    }

    public StringSetting placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public int getMaxLength() { return maxLength; }
    public String getPlaceholder() { return placeholder; }

    /** Checks a candidate without applying it (used by the text field to show validation state). */
    public boolean isAcceptable(String candidate) {
        return validate(candidate) != null;
    }

    @Override
    protected String validate(String candidate) {
        if (candidate == null) return null;
        // strip control characters
        StringBuilder sb = new StringBuilder(candidate.length());
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c >= 32 && c != 127) sb.append(c);
        }
        String v = sb.toString();
        if (validator != null && maxLength > 0) {
            if (v.length() > maxLength) v = v.substring(0, maxLength);
            if (!validator.test(v)) return null;
        }
        return v;
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
