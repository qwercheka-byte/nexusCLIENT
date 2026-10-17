package dev.nexus.client.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Base class of every configurable value.
 *
 * @param <T> value type
 */
public abstract class Setting<T> {
    private final String id;
    private final String name;
    private final String description;
    private T defaultValue;
    protected T value;
    private BooleanSupplier visibility = () -> true;
    private final List<Consumer<T>> listeners = new ArrayList<>(1);
    private SettingContainer owner;

    protected Setting(String id, String name, String description, T defaultValue) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        T valid = validate(defaultValue);
        if (valid == null) throw new IllegalArgumentException("Invalid default value for setting " + id);
        this.defaultValue = valid;
        this.value = valid;
    }

    /**
     * Normalises a candidate value.
     *
     * @return the (possibly corrected, e.g. clamped) value, or {@code null} if it can not be accepted
     */
    protected abstract T validate(T candidate);

    public abstract JsonElement toJson();

    /** Reads a value from JSON. Invalid data is ignored (the current value is kept). */
    public abstract void fromJson(JsonElement json);

    public T get() {
        return value;
    }

    /** @return {@code true} if the value was accepted (even if unchanged) */
    public boolean set(T candidate) {
        if (candidate == null) return false;
        T valid = validate(candidate);
        if (valid == null) return false;
        if (Objects.equals(valid, value)) return true;
        value = valid;
        for (int i = 0; i < listeners.size(); i++) listeners.get(i).accept(valid);
        if (owner != null) owner.onSettingChanged(this);
        return true;
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isDefault() {
        return Objects.equals(value, defaultValue);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public T getDefault() { return defaultValue; }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    /** Shows the setting in the UI only while {@code condition} is true. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = Objects.requireNonNull(condition);
        return (S) this;
    }

    /** Registers a change listener (called on the thread that changed the value). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> listener) {
        listeners.add(listener);
        return (S) this;
    }

    /**
     * Subclasses whose validation depends on their own fields (bounds, option lists) call this at the end
     * of their constructor: the super-constructor ran before those fields were assigned.
     */
    protected final void revalidateDefault() {
        T valid = validate(defaultValue);
        if (valid == null) throw new IllegalArgumentException("Invalid default value for setting " + id);
        this.defaultValue = valid;
        this.value = valid;
    }

    void bind(SettingContainer container) {
        this.owner = container;
    }
}
