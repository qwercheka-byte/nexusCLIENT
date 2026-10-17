package dev.nexus.client.ui.page;

import dev.nexus.client.Nexus;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.ScrollPanel;
import dev.nexus.client.ui.component.Component;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/** Version information, environment and keyboard shortcuts. */
public final class AboutPage extends Page {
    private final String[][] info;
    private final List<String[]> shortcuts = new ArrayList<>();
    private final Button openFolder;
    private final ScrollPanel scroll = new ScrollPanel();
    private final Content content = new Content();

    public AboutPage(NexusScreen screen) {
        super(screen);
        FabricLoader loader = FabricLoader.getInstance();
        String loaderVersion = loader.getModContainer("fabricloader").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String apiVersion = loader.getModContainer("fabric-api").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        info = new String[][]{
                {"Minecraft", Nexus.MC_VERSION},
                {"Fabric Loader", loaderVersion},
                {"Fabric API", apiVersion},
                {"Java", System.getProperty("java.version", "?")},
                {"Modules", String.valueOf(Nexus.modules().all().size())},
                {"Config", configPath(loader)},
        };
        shortcuts.add(new String[]{"Open / close menu", "menu"});
        shortcuts.add(new String[]{"Search modules", "Ctrl + F"});
        shortcuts.add(new String[]{"Switch pages", "Ctrl + 1…6"});
        shortcuts.add(new String[]{"Back / close", "Esc"});
        shortcuts.add(new String[]{"Reset a setting", "Right-click name"});
        shortcuts.add(new String[]{"Fine slider steps", "Shift + wheel / ← →"});
        shortcuts.add(new String[]{"HUD editor: free move", "Hold Shift"});
        shortcuts.add(new String[]{"HUD editor: nudge", "Arrows (Shift ×10)"});
        shortcuts.add(new String[]{"HUD editor: scale", "Mouse wheel"});
        shortcuts.add(new String[]{"HUD editor: reset / hide", "R / Delete"});
        shortcuts.add(new String[]{"HUD editor: grid / preview", "G / P"});
        openFolder = new Button("Open config folder", Icon.FOLDER, Button.Style.SECONDARY,
                () -> Util.getOperatingSystem().open(Nexus.config().root()));
        scroll.add(content);
    }

    private static String configPath(FabricLoader loader) {
        try {
            return loader.getGameDir().toAbsolutePath().relativize(Nexus.config().root().toAbsolutePath()).toString();
        } catch (IllegalArgumentException e) {
            return Nexus.config().root().toAbsolutePath().toString(); // different drive on Windows
        }
    }

    private static final String VERSION_LINE = "Version " + Nexus.VERSION + " · Fabric · Minecraft " + Nexus.MC_VERSION;

    @Override public String title() { return "About"; }
    @Override public String subtitle() { return "NEXUS Client " + Nexus.VERSION; }
    @Override public Icon icon() { return Icon.INFO; }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        scroll.setBounds(x, y, width, height);
        scroll.render(ctx, mx, my);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) { return scroll.mouseClicked(mx, my, button); }
    @Override public boolean mouseReleased(double mx, double my, int button) { return scroll.mouseReleased(mx, my, button); }
    @Override public boolean mouseScrolled(double mx, double my, double amount) { return scroll.mouseScrolled(mx, my, amount); }

    private final class Content extends Component {
        @Override
        public float preferredHeight(float width) {
            return 64 + 12 + info.length * 14 + 30 + 14 + shortcuts.size() * 13 + 10;
        }

        @Override
        public void render(DrawContext ctx, double mx, double my) {
            // brand block
            Render2D.roundedGradient(ctx, x, y, 44, 44, 11, Theme.accent(), ColorUtil.lerp(Theme.accent(), Theme.CYAN, 0.6f), true);
            IconRender.drawCentered(ctx, Icon.LOGO, x + 22, y + 22, 26, Theme.onAccent());
            TextRender.drawScaled(ctx, "NEXUS Client", x + 56, y + 6, 1.5f, Theme.TEXT, false);
            TextRender.draw(ctx, VERSION_LINE, x + 56, y + 22, Theme.TEXT_MUTED);
            TextRender.draw(ctx, TextRender.ellipsize("A clean, extensible utility client. MIT licensed.", (int) width - 60), x + 56, y + 33, Theme.TEXT_DIM);

            float ty = y + 64;
            TextRender.drawScaled(ctx, "ENVIRONMENT", x + 2, ty, 0.75f, Theme.accentBright(), false);
            ty += 12;
            float half = Math.min(width, 360);
            for (String[] row : info) {
                TextRender.draw(ctx, row[0], x + 2, ty, Theme.TEXT_MUTED);
                TextRender.drawRight(ctx, TextRender.ellipsize(row[1], (int) (half * 0.6f)), x + half, ty, Theme.TEXT);
                ty += 14;
            }
            float bw = openFolder.naturalWidth();
            openFolder.setBounds(x + 2, ty + 2, bw, 20);
            openFolder.render(ctx, mx, my);
            ty += 30;

            TextRender.drawScaled(ctx, "SHORTCUTS", x + 2, ty, 0.75f, Theme.accentBright(), false);
            ty += 14;
            for (String[] s : shortcuts) {
                String key = s[1].equals("menu") ? Nexus.settings().menuKey.keyName() : s[1];
                TextRender.draw(ctx, s[0], x + 2, ty, Theme.TEXT_MUTED);
                float kw = TextRender.width(key) + 8;
                Render2D.roundedRect(ctx, x + half - kw, ty - 2, kw, 12, 3, 0x14FFFFFF);
                TextRender.draw(ctx, key, x + half - kw + 4, ty, Theme.TEXT);
                ty += 13;
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return openFolder.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return openFolder.mouseReleased(mx, my, button);
        }
    }
}
