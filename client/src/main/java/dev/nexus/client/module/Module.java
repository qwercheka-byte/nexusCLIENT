package dev.nexus.client.module;

import dev.nexus.client.util.Lang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.client.Nexus;
import dev.nexus.client.NexusClient;
import dev.nexus.client.event.Subscription;
import dev.nexus.client.event.events.ModuleToggleEvent;
import dev.nexus.client.setting.KeybindSetting;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.setting.SettingContainer;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Base class of every feature.
 * <ul>
 *     <li>Settings are declared as fields via {@link #add(Setting)}.</li>
 *     <li>Event handlers declared via {@link #on(Class, Consumer)} are registered only while enabled.</li>
 *     <li>{@link #onEnable()} / {@link #onDisable()} must acquire / release any external state.</li>
 * </ul>
 */
public abstract class Module implements SettingContainer {
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean enabledByDefault;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);
    private final List<Subscription<?>> subscriptions = new ArrayList<>();
    private final KeybindSetting keybind;
    private String searchText;
    private int searchRevision = -1;
    private boolean enabled;

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabledByDefault = enabledByDefault;
        this.keybind = attach(new KeybindSetting("keybind", "Keybind", "Toggles " + name, KeybindSetting.NONE));
        this.keybind.describedBy(() -> Lang.fmt("Toggles {0}", getName()));
    }

    protected Module(String id, String name, String description, Category category) {
        this(id, name, description, category, false);
    }

    /** Declares a setting owned by this module. */
    protected <S extends Setting<?>> S add(S setting) {
        if (findSetting(setting.getId()) != null) {
            throw new IllegalStateException("Duplicate setting id '" + setting.getId() + "' in module " + id);
        }
        settings.add(attach(setting));
        return setting;
    }

    /** Declares an event handler that is active only while the module is enabled. */
    protected <T> void on(Class<T> type, Consumer<? super T> handler) {
        Subscription<T> sub = new Subscription<>(type, handler);
        subscriptions.add(sub);
        if (enabled) sub.register(Nexus.events());
    }

    /** Called right after the module got enabled (listeners are already registered). */
    protected void onEnable() {
    }

    /** Called when the module is being disabled (listeners are removed afterwards). Must release resources. */
    protected void onDisable() {
    }

    public final void toggle() {
        setEnabled(!enabled, false);
    }

    public final void setEnabled(boolean value) {
        setEnabled(value, false);
    }

    public final void setEnabled(boolean value, boolean byKeybind) {
        if (value == enabled) return;
        if (value) {
            enabled = true;
            for (Subscription<?> s : subscriptions) s.register(Nexus.events());
            try {
                onEnable();
            } catch (Throwable t) {
                NexusClient.LOGGER.error("Failed to enable module {}", id, t);
                enabled = false;
                for (Subscription<?> s : subscriptions) s.unregister(Nexus.events());
                return;
            }
        } else {
            enabled = false;
            try {
                onDisable();
            } catch (Throwable t) {
                NexusClient.LOGGER.error("Error while disabling module {}", id, t);
            } finally {
                for (Subscription<?> s : subscriptions) s.unregister(Nexus.events());
            }
        }
        Nexus.config().markProfileDirty();
        Nexus.events().post(new ModuleToggleEvent(this, enabled, byKeybind));
    }

    /** Runs {@link #onDisable()} for shutdown while keeping the enabled flag (it is already persisted). */
    final void releaseForShutdown() {
        if (!enabled) return;
        try {
            onDisable();
        } catch (Throwable t) {
            NexusClient.LOGGER.error("Error while releasing module {}", id, t);
        }
        for (Subscription<?> s : subscriptions) s.unregister(Nexus.events());
    }

    /** Restores every setting (and the keybind) to its default value. */
    public void resetSettings() {
        for (Setting<?> s : settings) s.reset();
        keybind.reset();
    }

    @Override
    public void onSettingChanged(Setting<?> setting) {
        Nexus.config().markProfileDirty();
    }

    // ---------------------------------------------------------------- serialisation

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", enabled);
        obj.add("key", keybind.toJson());
        JsonObject values = new JsonObject();
        for (Setting<?> s : settings) values.add(s.getId(), s.toJson());
        obj.add("settings", values);
        return obj;
    }

    /** Applies stored state. Unknown keys are ignored, missing keys fall back to defaults. */
    public void fromJson(JsonObject obj) {
        resetSettings();
        if (obj == null) {
            setEnabled(enabledByDefault);
            return;
        }
        JsonElement values = obj.get("settings");
        if (values != null && values.isJsonObject()) {
            for (Setting<?> s : settings) {
                JsonElement e = values.getAsJsonObject().get(s.getId());
                if (e != null) {
                    try {
                        s.fromJson(e);
                    } catch (RuntimeException ex) {
                        NexusClient.LOGGER.warn("Ignoring invalid value for {}.{}", id, s.getId());
                    }
                }
            }
        }
        if (obj.has("key")) keybind.fromJson(obj.get("key"));
        JsonElement en = obj.get("enabled");
        setEnabled(en != null && en.isJsonPrimitive() && en.getAsJsonPrimitive().isBoolean() ? en.getAsBoolean() : enabledByDefault);
    }

    // ---------------------------------------------------------------- accessors

    public String getId() { return id; }
    /** Display name in the game language. */
    public String getName() { return Lang.tr(name); }
    /** Description in the game language. */
    public String getDescription() { return Lang.tr(description); }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public boolean isEnabledByDefault() { return enabledByDefault; }
    public KeybindSetting getKeybind() { return keybind; }

    @Override
    public List<Setting<?>> getSettings() {
        return settingsView;
    }

    public boolean matches(String lowerCaseQuery) {
        if (lowerCaseQuery.isEmpty()) return true;
        int rev = Lang.revision();
        if (rev != searchRevision) {
            searchRevision = rev;
            // English and translated texts, so both "zoom" and "приближение" find the module
            searchText = (name + ' ' + description + ' ' + category.name() + ' ' + getName() + ' ' + getDescription() + ' '
                    + category.getDisplayName()).toLowerCase(Locale.ROOT);
        }
        return searchText.contains(lowerCaseQuery);
    }

    /** Whether the module appears in the on-screen module list HUD. */
    public boolean showInModuleList() {
        return true;
    }
}
