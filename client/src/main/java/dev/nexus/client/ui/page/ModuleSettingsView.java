package dev.nexus.client.ui.page;

import dev.nexus.client.hud.HudModule;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.IconButton;
import dev.nexus.client.ui.component.ScrollPanel;
import dev.nexus.client.ui.component.SectionHeader;
import dev.nexus.client.ui.component.SettingRow;
import dev.nexus.client.ui.component.Toggle;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Detail view with every setting of one module. */
final class ModuleSettingsView {
    private final NexusScreen screen;
    private final Runnable onBack;
    private final ScrollPanel list = new ScrollPanel().gap(3);
    private final IconButton back;
    private Module module;
    private String meta = "";
    private Toggle toggle;
    private final List<Button> footer = new ArrayList<>();
    private boolean confirmReset;
    private long confirmUntil;
    private float x, y, width, height;

    ModuleSettingsView(NexusScreen screen, Runnable onBack) {
        this.screen = screen;
        this.onBack = onBack;
        this.back = new IconButton(Icon.BACK, "Back (Esc)", onBack);
    }

    Module module() { return module; }

    void setModule(Module m) {
        this.module = m;
        this.meta = m.getCategory().getDisplayName() + "  ·  " + m.getId();
        this.toggle = new Toggle(m::isEnabled, v -> m.toggle());
        confirmReset = false;
        list.clear();
        list.scrollToTop();
        list.add(new SectionHeader("Activation"));
        list.add(new SettingRow(m.getKeybind()));
        List<Setting<?>> settings = m.getSettings();
        if (!settings.isEmpty()) {
            list.add(new SectionHeader(m instanceof HudModule ? "Element" : "Settings"));
            for (Setting<?> s : settings) list.add(new SettingRow(s));
        }
        footer.clear();
        footer.add(new Button("Reset to defaults", Icon.RESET, Button.Style.DANGER, this::resetClicked)
                .dynamicLabel(() -> confirmReset ? "Click again to reset" : "Reset to defaults"));
        if (m instanceof HudModule hud) {
            footer.add(new Button("Reset position", Icon.MOVE, Button.Style.SECONDARY, hud::resetLayout));
            footer.add(new Button("HUD editor", Icon.LAYOUT, Button.Style.PRIMARY, screen::openHudEditor));
        }
    }

    private void resetClicked() {
        long now = System.currentTimeMillis();
        if (confirmReset && now < confirmUntil) {
            module.resetSettings();
            confirmReset = false;
        } else {
            confirmReset = true;
            confirmUntil = now + 3000;
        }
    }

    void layout(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    void render(DrawContext ctx, double mx, double my) {
        if (module == null) return;
        if (confirmReset && System.currentTimeMillis() > confirmUntil) confirmReset = false;
        back.setBounds(x, y + 2, 18, 18);
        back.render(ctx, mx, my);
        float ix = x + 24;
        Render2D.roundedRect(ctx, ix, y, 22, 22, 6, module.isEnabled() ? Theme.accentSoft() : 0x14FFFFFF);
        IconRender.drawCentered(ctx, module.getCategory().getIcon(), ix + 11, y + 11, 12,
                module.isEnabled() ? Theme.accentBright() : Theme.TEXT_MUTED);
        TextRender.draw(ctx, module.getName(), ix + 30, y + 2, Theme.TEXT);
        TextRender.drawScaled(ctx, meta, ix + 30, y + 13, 0.7f, Theme.TEXT_DIM, false);
        toggle.setBounds(x + width - Toggle.WIDTH - 2, y + 5, Toggle.WIDTH, Toggle.HEIGHT);
        toggle.render(ctx, mx, my);
        TextRender.draw(ctx, TextRender.ellipsize(module.getDescription(), (int) width - 4), x + 2, y + 30, Theme.TEXT_MUTED);
        Render2D.rect(ctx, x, y + 43, width, 1, Theme.DIVIDER);

        float footerY = y + height - 20;
        list.setBounds(x, y + 48, width, footerY - 6 - (y + 48));
        list.render(ctx, mx, my);

        float fx = x;
        for (Button b : footer) {
            float bw = b.naturalWidth();
            b.setBounds(fx, footerY, bw, 20);
            b.render(ctx, mx, my);
            fx += bw + 6;
        }
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (back.mouseClicked(mx, my, button)) return true;
        if (toggle != null && toggle.mouseClicked(mx, my, button)) return true;
        for (Button b : footer) if (b.mouseClicked(mx, my, button)) return true;
        return list.mouseClicked(mx, my, button);
    }

    boolean mouseReleased(double mx, double my, int button) {
        boolean handled = back.mouseReleased(mx, my, button);
        for (Button b : footer) handled |= b.mouseReleased(mx, my, button);
        return list.mouseReleased(mx, my, button) || handled;
    }

    boolean mouseScrolled(double mx, double my, double amount) { return list.mouseScrolled(mx, my, amount); }
    boolean keyPressed(int key, int scancode, int modifiers) { return list.keyPressed(key, scancode, modifiers); }
    boolean charTyped(char chr, int modifiers) { return list.charTyped(chr, modifiers); }
    void back() { onBack.run(); }
}
