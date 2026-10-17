package dev.nexus.client.ui.page;

import dev.nexus.client.Nexus;
import dev.nexus.client.hud.HudModule;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.Component;
import dev.nexus.client.ui.component.IconButton;
import dev.nexus.client.ui.component.ScrollPanel;
import dev.nexus.client.ui.component.Toggle;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** HUD overview: open the editor and manage every HUD element. */
public final class HudPage extends Page {
    private final Button openEditor;
    private final Button resetAll;
    private final ScrollPanel list = new ScrollPanel().gap(4);
    private boolean confirmResetAll;
    private long confirmUntil;
    private int countFor = -1;
    private String countText = "";

    public HudPage(NexusScreen screen) {
        super(screen);
        openEditor = new Button("Open HUD editor", Icon.LAYOUT, Button.Style.PRIMARY, screen::openHudEditor);
        resetAll = new Button("Reset all positions", Icon.RESET, Button.Style.GHOST, this::resetAllClicked)
                .dynamicLabel(() -> confirmResetAll ? "Click again to confirm" : "Reset all positions");
        for (HudModule m : Nexus.modules().hudModules()) list.add(new Row(m));
    }

    @Override public String title() { return "HUD Editor"; }
    @Override public Icon icon() { return Icon.HUD; }

    @Override
    public String subtitle() {
        int enabled = 0;
        for (HudModule m : Nexus.modules().hudModules()) if (m.isEnabled()) enabled++;
        if (enabled != countFor) {
            countFor = enabled;
            countText = enabled + " of " + Nexus.modules().hudModules().size() + " elements visible";
        }
        return countText;
    }

    private void resetAllClicked() {
        long now = System.currentTimeMillis();
        if (confirmResetAll && now < confirmUntil) {
            for (HudModule m : Nexus.modules().hudModules()) m.resetLayout();
            confirmResetAll = false;
        } else {
            confirmResetAll = true;
            confirmUntil = now + 3000;
        }
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        if (confirmResetAll && System.currentTimeMillis() > confirmUntil) confirmResetAll = false;
        float ch = 52;
        Render2D.roundedGradient(ctx, x, y, width, ch, Theme.RADIUS, Theme.CARD, ColorUtil.lerp(Theme.CARD, Theme.accent(), 0.12f), true);
        Render2D.roundedOutline(ctx, x, y, width, ch, Theme.RADIUS, 1f, Theme.BORDER);
        Render2D.roundedRect(ctx, x + 10, y + 12, 28, 28, 7, Theme.accentSoft());
        IconRender.drawCentered(ctx, Icon.LAYOUT, x + 24, y + 26, 15, Theme.accentBright());
        float bw = openEditor.naturalWidth();
        TextRender.draw(ctx, "Layout editor", x + 46, y + 14, Theme.TEXT);
        TextRender.draw(ctx, TextRender.ellipsize("Drag, scale and snap elements with grid and guides", (int) (width - 64 - bw)),
                x + 46, y + 27, Theme.TEXT_MUTED);
        openEditor.setBounds(x + width - bw - 12, y + 16, bw, 20);
        openEditor.render(ctx, mx, my);

        float ly = y + ch + 10;
        TextRender.drawScaled(ctx, "ELEMENTS", x + 2, ly, 0.75f, Theme.accentBright(), false);
        float rw = resetAll.naturalWidth();
        resetAll.setBounds(x + width - rw, ly - 6, rw, 18);
        resetAll.render(ctx, mx, my);
        list.setBounds(x, ly + 14, width, y + height - ly - 14);
        list.render(ctx, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (openEditor.mouseClicked(mx, my, button) || resetAll.mouseClicked(mx, my, button)) return true;
        return list.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean a = openEditor.mouseReleased(mx, my, button);
        boolean b = resetAll.mouseReleased(mx, my, button);
        return list.mouseReleased(mx, my, button) || a || b;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return list.mouseScrolled(mx, my, amount);
    }

    /** One HUD element: toggle, scale/opacity summary, reset and settings shortcuts. */
    private final class Row extends Component {
        private final HudModule module;
        private final Toggle toggle;
        private final IconButton reset, settings;
        private double shownScale = Double.NaN, shownOpacity = Double.NaN;
        private String info = "";

        Row(HudModule module) {
            this.module = module;
            this.toggle = new Toggle(module::isEnabled, v -> module.toggle());
            this.reset = new IconButton(Icon.RESET, "Reset position & scale", module::resetLayout);
            this.settings = new IconButton(Icon.SETTINGS, "Settings", () -> screen.openModuleSettings(module));
            this.height = 30;
        }

        @Override
        public float preferredHeight(float width) {
            return 30;
        }

        @Override
        public void render(DrawContext ctx, double mx, double my) {
            float h = updateHover(mx, my);
            Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS_SMALL, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, h));
            Render2D.circle(ctx, x + 12, y + height / 2f, 2.5f, module.isEnabled() ? Theme.SUCCESS : 0xFF4A4B57);
            if (module.getScale() != shownScale || module.getOpacity() != shownOpacity) {
                shownScale = module.getScale();
                shownOpacity = module.getOpacity();
                info = String.format(java.util.Locale.ROOT, "Scale %.2fx · Opacity %d%%", shownScale, Math.round(shownOpacity * 100));
            }
            float right = x + width - 8 - Toggle.WIDTH;
            toggle.setBounds(right, y + (height - Toggle.HEIGHT) / 2f, Toggle.WIDTH, Toggle.HEIGHT);
            settings.setBounds(right - 24, y + 6, 18, 18);
            reset.setBounds(right - 44, y + 6, 18, 18);
            float textW = right - 50 - (x + 22);
            TextRender.draw(ctx, TextRender.ellipsize(module.getName(), (int) textW), x + 22, y + 6, Theme.TEXT);
            TextRender.drawScaled(ctx, TextRender.ellipsize(info, (int) (textW / 0.7f)), x + 22, y + 17, 0.7f, Theme.TEXT_DIM, false);
            reset.render(ctx, mx, my);
            settings.render(ctx, mx, my);
            toggle.render(ctx, mx, my);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return toggle.mouseClicked(mx, my, button) || reset.mouseClicked(mx, my, button) || settings.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean a = reset.mouseReleased(mx, my, button);
            return settings.mouseReleased(mx, my, button) || a;
        }
    }
}
