package dev.nexus.client.module.impl.hud;

import dev.nexus.client.util.Lang;
import dev.nexus.client.Nexus;
import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.ui.animation.Animation;
import dev.nexus.client.ui.animation.Easing;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Animated list of enabled modules. */
public final class ModuleListHudModule extends HudModule {
    private final BooleanSetting hideHud = add(new BooleanSetting("hide_hud", "Hide HUD modules",
            "Do not list HUD elements (they are visible anyway)", true));
    private final ModeSetting sort = add(new ModeSetting("sort", "Sort", "Order of entries", "Width", "Width", "Alphabetical"));
    private final BooleanSetting accentBar = add(new BooleanSetting("accent_bar", "Accent bar", "Accent line beside each entry", true));
    private final BooleanSetting lowercase = add(new BooleanSetting("lowercase", "Lowercase", "Display names in lowercase", false));

    private static final class Entry {
        final Module module;
        final Animation anim = new Animation(0f, 240, Easing.OUT_CUBIC);
        String text;
        int width;

        Entry(Module module) {
            this.module = module;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int builtStamp = -1, changes, builtLang = -1;

    public ModuleListHudModule() {
        super("module_list", "Module List", "Animated list of your enabled modules.", false, END, START, 4, 4);
    }

    @Override
    public boolean showInModuleList() {
        return false;
    }

    @Override
    public void onSettingChanged(dev.nexus.client.setting.Setting<?> setting) {
        changes++;
        super.onSettingChanged(setting);
    }

    private void rebuild() {
        builtStamp = changes;
        builtLang = Lang.revision();
        List<Entry> old = new ArrayList<>(entries);
        entries.clear();
        for (Module m : Nexus.modules().all()) {
            if (!m.showInModuleList() || (hideHud.isOn() && m.getCategory() == Category.HUD)) continue;
            Entry e = null;
            for (Entry o : old) if (o.module == m) { e = o; break; }
            if (e == null) {
                e = new Entry(m);
                e.anim.set(m.isEnabled() ? 1f : 0f);
            }
            e.text = lowercase.isOn() ? m.getName().toLowerCase(Locale.ROOT) : m.getName();
            e.width = TextRender.width(e.text);
            entries.add(e);
        }
        entries.sort(sort.is("Alphabetical") ? Comparator.comparing((Entry e) -> e.text)
                : Comparator.comparingInt((Entry e) -> -e.width).thenComparing(e -> e.text));
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        if (builtStamp != changes || builtLang != Lang.revision() || entries.isEmpty()) rebuild();
        boolean right = isRightAligned();
        float rowH = 11f, padX = 4f;
        float maxW = 0, totalH = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            e.anim.animateTo(e.module.isEnabled() ? 1f : 0f);
            float a = e.anim.get();
            if (a <= 0.001f) continue;
            maxW = Math.max(maxW, e.width + padX * 2 + (accentBar.isOn() ? 2 : 0));
            totalH += rowH * a;
        }
        if (totalH <= 0.5f) {
            if (!editor) return false;
            String hint = "No active modules";
            float w = TextRender.width(hint) + padX * 2;
            setSize(w, rowH);
            HudStyle.panel(ctx, this, w, rowH);
            HudStyle.text(ctx, hint, padX, 2, HudStyle.LABEL);
            return true;
        }
        setSize(maxW, totalH);
        float y = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float a = e.anim.get();
            if (a <= 0.001f) continue;
            float w = e.width + padX * 2 + (accentBar.isOn() ? 2 : 0);
            float slide = (1f - a) * (w + 4);
            float x = right ? maxW - w + slide : -slide;
            Render2D.pushAlpha(a);
            if (hasBackground()) Render2D.rect(ctx, x, y, w, rowH * a, HudStyle.PANEL);
            if (accentBar.isOn()) Render2D.rect(ctx, right ? x + w - 1.5f : x, y, 1.5f, rowH * a, Theme.accent());
            float tx = right ? x + padX : x + padX + 2;
            HudStyle.text(ctx, e.text, tx, y + 2, HudStyle.VALUE);
            Render2D.popAlpha();
            y += rowH * a;
        }
        return true;
    }
}
