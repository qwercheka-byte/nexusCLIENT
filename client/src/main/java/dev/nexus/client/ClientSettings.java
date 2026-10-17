package dev.nexus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ColorSetting;
import dev.nexus.client.setting.KeybindSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.setting.SettingContainer;
import dev.nexus.client.ui.animation.AnimationSettings;
import dev.nexus.client.ui.theme.Theme;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Global (profile independent) client settings stored in client.json. */
public final class ClientSettings implements SettingContainer {
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> view = Collections.unmodifiableList(settings);

    // --- General
    public final KeybindSetting menuKey = add(new KeybindSetting("menu_key", "Menu key",
            "Opens the NEXUS menu while in game", GLFW.GLFW_KEY_RIGHT_SHIFT));
    public final BooleanSetting pauseInMenu = add(new BooleanSetting("pause_in_menu", "Pause singleplayer",
            "Pause the integrated server while the menu is open", false));
    public final BooleanSetting tooltips = add(new BooleanSetting("tooltips", "Tooltips",
            "Show descriptions when hovering settings", true));

    // --- Theme
    public final ModeSetting accentPreset = add(new ModeSetting("accent_preset", "Accent",
            "Accent colour of the interface", "Violet", "Violet", "Cyan", "Emerald", "Rose", "Amber", "Custom"));
    public final ColorSetting customAccent = add(new ColorSetting("custom_accent", "Custom accent",
            "Your own accent colour", 0xFF6C8CFF, false)).visibleWhen(() -> accentPreset.is("Custom"));

    // --- Interface
    public final NumberSetting uiScale = add(new NumberSetting("ui_scale", "Menu scale",
            "Multiplier on top of the automatic menu scale", 1.0, 0.6, 1.5, 0.05).displayMultiplier(100).suffix("%"));
    public final NumberSetting animationSpeed = add(new NumberSetting("animation_speed", "Animation speed",
            "Global animation speed (10x disables animations)", 1.0, 0.25, 10.0, 0.25).suffix("x"));
    public final BooleanSetting backgroundBlur = add(new BooleanSetting("background_blur", "Background blur",
            "Blur the world behind the menu (uses the vanilla blur strength)", true));
    public final NumberSetting backgroundDim = add(new NumberSetting("background_dim", "Background dim",
            "Darkening behind the menu", 0.45, 0.0, 0.9, 0.05).displayMultiplier(100).suffix("%"));

    // --- HUD
    public final BooleanSetting hudHideInDebug = add(new BooleanSetting("hud_hide_debug", "Hide HUD with F3",
            "Hide NEXUS HUD elements while the debug screen is open", true));
    public final BooleanSetting hudAccentLine = add(new BooleanSetting("hud_accent_line", "HUD accent line",
            "Thin accent line on HUD panels", true));
    public final NumberSetting hudRadius = add(new NumberSetting("hud_radius", "HUD corner radius",
            "Corner radius of HUD panels", 3, 0, 8, 0.5).suffix("px"));
    public final BooleanSetting hudTextShadow = add(new BooleanSetting("hud_text_shadow", "HUD text shadow",
            "Draw a shadow under HUD text", false));

    // --- HUD editor
    public final NumberSetting editorGrid = add(new NumberSetting("editor_grid", "Editor grid size",
            "Spacing of the HUD editor grid", 10, 4, 40, 1).suffix("px"));
    public final BooleanSetting editorSnap = add(new BooleanSetting("editor_snap", "Snapping",
            "Snap elements to edges, centre and other elements (hold Shift to bypass)", true));
    public final BooleanSetting editorShowGrid = add(new BooleanSetting("editor_show_grid", "Show grid",
            "Show the grid in the HUD editor", true));

    public ClientSettings() {
        accentPreset.onChange(v -> applyTheme());
        customAccent.onChange(v -> applyTheme());
        animationSpeed.onChange(v -> AnimationSettings.setSpeed(v.floatValue()));
        applyTheme();
    }

    private <S extends Setting<?>> S add(S s) {
        settings.add(attach(s));
        return s;
    }

    public void applyTheme() {
        Theme.setAccent(switch (accentPreset.get()) {
            case "Cyan" -> Theme.CYAN;
            case "Emerald" -> Theme.EMERALD;
            case "Rose" -> Theme.ROSE;
            case "Amber" -> Theme.AMBER;
            case "Custom" -> customAccent.get();
            default -> Theme.VIOLET;
        });
        AnimationSettings.setSpeed(animationSpeed.getFloat());
    }

    @Override
    public List<Setting<?>> getSettings() {
        return view;
    }

    @Override
    public void onSettingChanged(Setting<?> setting) {
        Nexus.config().markClientDirty();
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        for (Setting<?> s : settings) obj.add(s.getId(), s.toJson());
        return obj;
    }

    public void fromJson(JsonObject obj) {
        for (Setting<?> s : settings) {
            s.reset();
            JsonElement e = obj == null ? null : obj.get(s.getId());
            if (e != null) {
                try {
                    s.fromJson(e);
                } catch (RuntimeException ex) {
                    NexusClient.LOGGER.warn("Ignoring invalid client setting {}", s.getId());
                }
            }
        }
        applyTheme();
    }
}
