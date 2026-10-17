package dev.nexus.client.ui.page;

import dev.nexus.client.ClientSettings;
import dev.nexus.client.Nexus;
import dev.nexus.client.render.Icon;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.ScrollPanel;
import dev.nexus.client.ui.component.SectionHeader;
import dev.nexus.client.ui.component.SettingRow;
import net.minecraft.client.gui.DrawContext;

/** Global client settings (stored in client.json, shared by all profiles). */
public final class SettingsPage extends Page {
    private final ScrollPanel list = new ScrollPanel().gap(3);
    private final Button reset;
    private boolean confirm;
    private long confirmUntil;

    public SettingsPage(NexusScreen screen) {
        super(screen);
        ClientSettings s = Nexus.settings();
        section("General", s.menuKey, s.pauseInMenu, s.tooltips);
        section("Theme", s.accentPreset, s.customAccent);
        section("Interface", s.uiScale, s.animationSpeed, s.backgroundBlur, s.backgroundDim);
        section("HUD", s.hudHideInDebug, s.hudAccentLine, s.hudRadius, s.hudTextShadow);
        section("HUD Editor", s.editorSnap, s.editorShowGrid, s.editorGrid);
        // any settings added to ClientSettings later still appear
        java.util.Set<Setting<?>> listed = new java.util.HashSet<>();
        for (var c : list.children()) if (c instanceof SettingRow r) listed.add(r.setting());
        boolean header = false;
        for (Setting<?> setting : s.getSettings()) {
            if (listed.contains(setting)) continue;
            if (!header) {
                list.add(new SectionHeader("Other"));
                header = true;
            }
            list.add(new SettingRow(setting));
        }
        reset = new Button("Reset client settings", Icon.RESET, Button.Style.DANGER, this::resetClicked)
                .dynamicLabel(() -> confirm ? "Click again to reset" : "Reset client settings");
    }

    private void section(String title, Setting<?>... settings) {
        list.add(new SectionHeader(title));
        for (Setting<?> s : settings) list.add(new SettingRow(s));
    }

    private void resetClicked() {
        long now = System.currentTimeMillis();
        if (confirm && now < confirmUntil) {
            for (Setting<?> s : Nexus.settings().getSettings()) s.reset();
            confirm = false;
        } else {
            confirm = true;
            confirmUntil = now + 3000;
        }
    }

    @Override public String title() { return "Settings"; }
    @Override public String subtitle() { return "Client-wide preferences, shared by all profiles"; }
    @Override public Icon icon() { return Icon.SETTINGS; }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        if (confirm && System.currentTimeMillis() > confirmUntil) confirm = false;
        float footerY = y + height - 20;
        list.setBounds(x, y, width, footerY - 6 - y);
        list.render(ctx, mx, my);
        reset.setBounds(x, footerY, reset.naturalWidth(), 20);
        reset.render(ctx, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return reset.mouseClicked(mx, my, button) || list.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean a = reset.mouseReleased(mx, my, button);
        return list.mouseReleased(mx, my, button) || a;
    }

    @Override public boolean mouseScrolled(double mx, double my, double amount) { return list.mouseScrolled(mx, my, amount); }
    @Override public boolean keyPressed(int key, int scancode, int modifiers) { return list.keyPressed(key, scancode, modifiers); }
    @Override public boolean charTyped(char chr, int modifiers) { return list.charTyped(chr, modifiers); }
}
