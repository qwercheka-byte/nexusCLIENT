package dev.nexus.client.module;

import dev.nexus.client.Nexus;
import dev.nexus.client.event.events.KeyEvent;
import dev.nexus.client.event.events.MouseButtonEvent;
import dev.nexus.client.hud.HudModule;
import dev.nexus.client.util.Mc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of all modules; also dispatches module hotkeys. */
public final class ModuleManager {
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final Map<Class<? extends Module>, Module> byClass = new LinkedHashMap<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
    private final List<Module> all = new ArrayList<>();
    private final List<HudModule> hudModules = new ArrayList<>();

    public void register(Module module) {
        if (byId.containsKey(module.getId())) throw new IllegalStateException("Duplicate module id " + module.getId());
        byId.put(module.getId(), module);
        byClass.put(module.getClass(), module);
        byCategory.computeIfAbsent(module.getCategory(), c -> new ArrayList<>()).add(module);
        all.add(module);
        if (module instanceof HudModule hud) hudModules.add(hud);
    }

    public void init() {
        Nexus.events().subscribe(KeyEvent.class, this::onKey);
        Nexus.events().subscribe(MouseButtonEvent.class, this::onMouse);
    }

    private boolean acceptsHotkeys() {
        MinecraftClient mc = Mc.get();
        if (mc.currentScreen != null || mc.player == null) return false;
        // F3+<key> debug combos must not toggle modules
        return !InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_F3);
    }

    private void onKey(KeyEvent e) {
        if (!e.isPress() || !acceptsHotkeys()) return;
        for (int i = 0; i < all.size(); i++) {
            Module m = all.get(i);
            if (m.getKeybind().matchesKey(e.key())) m.setEnabled(!m.isEnabled(), true);
        }
    }

    private void onMouse(MouseButtonEvent e) {
        if (!e.isPress() || !acceptsHotkeys()) return;
        for (int i = 0; i < all.size(); i++) {
            Module m = all.get(i);
            if (m.getKeybind().matchesMouse(e.button())) m.setEnabled(!m.isEnabled(), true);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) byClass.get(type);
    }

    public Module byId(String id) {
        return byId.get(id);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(all);
    }

    public List<Module> byCategory(Category category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public List<HudModule> hudModules() {
        return Collections.unmodifiableList(hudModules);
    }

    public int enabledCount() {
        int n = 0;
        for (Module m : all) if (m.isEnabled()) n++;
        return n;
    }

    /**
     * Releases external state of all enabled modules on game shutdown (e.g. restores gamma) without
     * changing their persisted enabled flag. Must be called after the configuration was saved.
     */
    public void shutdown() {
        for (Module m : all) m.releaseForShutdown();
    }
}
