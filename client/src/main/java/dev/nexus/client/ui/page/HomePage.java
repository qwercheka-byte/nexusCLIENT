package dev.nexus.client.ui.page;

import dev.nexus.client.util.Lang;
import dev.nexus.client.Nexus;
import dev.nexus.client.Session;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.Toggle;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import dev.nexus.client.util.TimeFormat;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Overview: session statistics, quick toggles and shortcuts. */
public final class HomePage extends Page {
    private static final int QUICK_COUNT = 6;
    private static final String BANNER_LINE = "NEXUS " + Nexus.VERSION + " · Minecraft " + Nexus.MC_VERSION;
    private final List<Module> quick = new ArrayList<>();
    private final List<Toggle> quickToggles = new ArrayList<>();
    private final SmoothValue[] quickHover = new SmoothValue[QUICK_COUNT];
    private final List<Button> actions = new ArrayList<>();

    private long statsSecond = -1;
    private String greeting = "", sessionValue = "", sessionLabel = "", serverValue = "", serverLabel = "",
            modulesValue = "", profileValue = "";

    public HomePage(NexusScreen screen) {
        super(screen);
        for (Module m : Nexus.modules().all()) {
            if (m.getCategory() == Category.HUD || m.getCategory() == Category.INTERFACE) continue;
            if (quick.size() < QUICK_COUNT) quick.add(m);
        }
        for (Module m : Nexus.modules().hudModules()) {
            if (quick.size() < QUICK_COUNT) quick.add(m);
        }
        for (int i = 0; i < quick.size(); i++) {
            Module m = quick.get(i);
            quickToggles.add(new Toggle(m::isEnabled, v -> m.toggle()));
            quickHover[i] = new SmoothValue(0f, 18f);
        }
        actions.add(new Button("Browse modules", Icon.MODULES, Button.Style.PRIMARY, () -> screen.showPage(NexusScreen.MODULES)));
        actions.add(new Button("HUD editor", Icon.LAYOUT, Button.Style.SECONDARY, screen::openHudEditor));
        actions.add(new Button("Profiles", Icon.PROFILES, Button.Style.SECONDARY, () -> screen.showPage(NexusScreen.PROFILES)));
    }

    @Override public String title() { return "Home"; }
    @Override public String subtitle() { return "Overview of your session and client"; }
    @Override public Icon icon() { return Icon.HOME; }

    private void refreshStats() {
        long second = System.currentTimeMillis() / 1000L;
        if (second == statsSecond) return;
        statsSecond = second;
        Session s = Nexus.session();
        greeting = Lang.fmt("Welcome back, {0}", s.playerName());
        if (s.inSession()) {
            sessionLabel = "In world";
            sessionValue = TimeFormat.humanDuration(s.sessionSeconds());
            serverLabel = s.ping() >= 0 ? s.ping() + " ms" : "Server";
            serverValue = s.serverName();
        } else {
            sessionLabel = "Game uptime";
            sessionValue = TimeFormat.humanDuration(s.uptimeSeconds());
            serverLabel = "Server";
            serverValue = "Not connected";
        }
        modulesValue = Nexus.modules().enabledCount() + " / " + Nexus.modules().all().size();
        profileValue = Nexus.config().profiles().getActive();
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        refreshStats();
        // hero banner
        float bh = 50;
        Render2D.roundedGradient(ctx, x, y, width, bh, Theme.RADIUS, ColorUtil.withAlpha(Theme.accent(), 0x38),
                ColorUtil.withAlpha(Theme.CYAN, 0x10), true);
        Render2D.roundedOutline(ctx, x, y, width, bh, Theme.RADIUS, 1f, ColorUtil.withAlpha(Theme.accent(), 0x50));
        TextRender.drawScaled(ctx, TextRender.ellipsize(greeting, (int) ((width - 24) / 1.2f)), x + 12, y + 12, 1.2f, Theme.TEXT, false);
        TextRender.draw(ctx, BANNER_LINE, x + 12, y + 29, Theme.TEXT_MUTED);

        // stat cards
        float sy = y + bh + 10;
        int cols = width < 360 ? 2 : 4;
        float gap = 8, cw = (width - gap * (cols - 1)) / cols, ch = 40;
        statCard(ctx, x, sy, cw, ch, Icon.MODULES, "Modules enabled", modulesValue);
        statCard(ctx, x + (cw + gap), sy, cw, ch, Icon.PROFILES, "Active profile", profileValue);
        float row2y = cols == 2 ? sy + ch + gap : sy;
        float row2x = cols == 2 ? 0 : 2;
        statCard(ctx, x + (cw + gap) * row2x, row2y, cw, ch, Icon.BOLT, sessionLabel, sessionValue);
        statCard(ctx, x + (cw + gap) * (row2x + 1), row2y, cw, ch, Icon.MONITOR, serverLabel, serverValue);

        // quick toggles
        float qy = row2y + ch + 14;
        TextRender.drawScaled(ctx, "QUICK TOGGLES", x + 2, qy, 0.75f, Theme.accentBright(), false);
        qy += 10;
        int qcols = width < 360 ? 2 : 3;
        float qw = (width - gap * (qcols - 1)) / qcols, qh = 24;
        for (int i = 0; i < quick.size(); i++) {
            float qx = x + (i % qcols) * (qw + gap), yy = qy + (i / qcols) * (qh + 6);
            Module m = quick.get(i);
            boolean hov = MathUtil.inside(mx, my, qx, yy, qw, qh) && UiContext.inClip(mx, my);
            float h = quickHover[i].update(hov ? 1f : 0f);
            Render2D.roundedRect(ctx, qx, yy, qw, qh, Theme.RADIUS_SMALL, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, h));
            IconRender.draw(ctx, m.getCategory().getIcon(), qx + 7, yy + 7, 10, m.isEnabled() ? Theme.accentBright() : Theme.TEXT_DIM);
            TextRender.draw(ctx, TextRender.ellipsize(m.getName(), (int) qw - 54), qx + 22, TextRender.centerY(yy, qh), Theme.TEXT);
            Toggle t = quickToggles.get(i);
            t.setBounds(qx + qw - Toggle.WIDTH - 6, yy + (qh - Toggle.HEIGHT) / 2f, Toggle.WIDTH, Toggle.HEIGHT);
            t.render(ctx, mx, my);
            if (hov && !t.isHovered(mx, my)) UiContext.tooltip(m.getDescription() + "\n\n" + Lang.tr("Click to open settings"), mx, my);
        }
        int qrows = (quick.size() + qcols - 1) / qcols;

        // actions + tip
        float ay = qy + qrows * (qh + 6) + 6;
        if (ay + 20 <= y + height) {
            float ax = x;
            for (Button b : actions) {
                float w = b.naturalWidth();
                b.setBounds(ax, ay, w, 20);
                b.render(ctx, mx, my);
                ax += w + 6;
            }
        }
        if (ay + 40 <= y + height) {
            TextRender.draw(ctx, TextRender.ellipsize("Tip: Ctrl+F searches modules, right-click a setting name to reset it.", (int) width),
                    x + 2, y + height - 10, Theme.TEXT_DIM);
        }
    }

    private void statCard(DrawContext ctx, float cx, float cy, float w, float h, Icon icon, String label, String value) {
        Render2D.roundedRect(ctx, cx, cy, w, h, Theme.RADIUS, Theme.CARD);
        Render2D.roundedOutline(ctx, cx, cy, w, h, Theme.RADIUS, 1f, Theme.BORDER);
        Render2D.circle(ctx, cx + 16, cy + h / 2f, 9, Theme.accentFaint());
        IconRender.drawCentered(ctx, icon, cx + 16, cy + h / 2f, 10, Theme.accentBright());
        TextRender.drawScaled(ctx, TextRender.ellipsize(label, (int) ((w - 34) / 0.7f)), cx + 30, cy + 10, 0.7f, Theme.TEXT_DIM, false);
        TextRender.draw(ctx, TextRender.ellipsize(value, (int) (w - 34)), cx + 30, cy + 20, Theme.TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Toggle t : quickToggles) if (t.mouseClicked(mx, my, button)) return true;
        for (Button b : actions) if (b.mouseClicked(mx, my, button)) return true;
        for (int i = 0; i < quick.size(); i++) {
            float yy = quickToggles.get(i).getY() - (24 - Toggle.HEIGHT) / 2f;
            // click anywhere on the tile except the toggle opens the module settings
            if (button == 0 && MathUtil.inside(mx, my, tileX(i), yy, tileW(), 24)) {
                Button.playClick();
                screen.openModuleSettings(quick.get(i));
                return true;
            }
        }
        return false;
    }

    private float tileW() {
        int qcols = width < 360 ? 2 : 3;
        return (width - 8 * (qcols - 1)) / qcols;
    }

    private float tileX(int i) {
        int qcols = width < 360 ? 2 : 3;
        return x + (i % qcols) * (tileW() + 8);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = false;
        for (Button b : actions) handled |= b.mouseReleased(mx, my, button);
        return handled;
    }
}
