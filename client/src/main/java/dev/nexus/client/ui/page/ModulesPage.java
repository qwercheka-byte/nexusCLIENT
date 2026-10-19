package dev.nexus.client.ui.page;

import dev.nexus.client.util.Lang;
import dev.nexus.client.Nexus;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.Animation;
import dev.nexus.client.ui.animation.Easing;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.Component;
import dev.nexus.client.ui.component.ModuleCard;
import dev.nexus.client.ui.component.ScrollPanel;
import dev.nexus.client.ui.component.TextField;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Module browser: search, category filter, animated card grid and per-module settings view. */
public final class ModulesPage extends Page {
    private static final float GRID_GAP = 8, MIN_CARD_W = 150;

    private final TextField search;
    private final List<ModuleCard> cards = new ArrayList<>();
    private final List<ModuleCard> visible = new ArrayList<>();
    private final Grid grid = new Grid();
    private final ModuleSettingsView settingsView;
    private final Animation viewAnim = new Animation(0f, 240, Easing.OUT_CUBIC);
    private final Category[] categories = Category.values();
    private final SmoothValue chipIndicatorX = new SmoothValue(0f, 16f), chipIndicatorW = new SmoothValue(0f, 16f);
    private final float[] chipX, chipW;
    private final SmoothValue[] chipHover;
    private final String[] chipLabels;
    private int category = -1; // -1 = all
    private String query = "";
    private boolean inSettings;
    private int returnPage = -1;
    private float chipsY;
    private String countText = "";
    private int countFor = -1;

    public ModulesPage(NexusScreen screen) {
        super(screen);
        search = new TextField().placeholder("Search modules").icon(Icon.SEARCH).maxLength(32)
                .onChange(q -> {
                    query = q.trim().toLowerCase(Locale.ROOT);
                    refilter();
                });
        for (Module m : Nexus.modules().all()) cards.add(new ModuleCard(m, this::openFromGrid));
        settingsView = new ModuleSettingsView(screen, this::closeSettings);
        chipLabels = new String[categories.length + 1];
        chipLabels[0] = "All";
        for (int i = 0; i < categories.length; i++) chipLabels[i + 1] = categories[i].getDisplayName();
        chipX = new float[chipLabels.length];
        chipW = new float[chipLabels.length];
        chipHover = new SmoothValue[chipLabels.length];
        for (int i = 0; i < chipHover.length; i++) chipHover[i] = new SmoothValue(0f, 18f);
        refilter();
    }

    @Override public String title() { return "Modules"; }
    @Override public Icon icon() { return Icon.MODULES; }

    @Override
    public String subtitle() {
        int enabled = Nexus.modules().enabledCount();
        if (enabled != countFor) {
            countFor = enabled;
            countText = Lang.fmt("{0} of {1} enabled · click a card for settings", enabled, Nexus.modules().all().size());
        }
        return countText;
    }

    public void focusSearch() {
        if (inSettings) closeSettings();
        search.focus();
    }

    private void refilter() {
        List<ModuleCard> before = new ArrayList<>(visible);
        visible.clear();
        for (ModuleCard c : cards) {
            Module m = c.module();
            if (category >= 0 && m.getCategory() != categories[category]) continue;
            if (!query.isEmpty() && !m.matches(query)) continue;
            visible.add(c);
        }
        for (ModuleCard c : visible) if (!before.contains(c)) c.hideInstantly();
        grid.children().clear();
        grid.children().addAll(visible);
        grid.scrollToTop();
    }

    // ------------------------------------------------------------------ settings view

    private void openFromGrid(Module m) {
        openSettings(m, -1);
    }

    /**
     * Shows the settings of {@code m}.
     *
     * @param fromPage page to return to when the user goes back, or -1 to return to the grid
     */
    public void openSettings(Module m, int fromPage) {
        UiContext.unfocus(UiContext.focused());
        settingsView.setModule(m);
        inSettings = true;
        returnPage = fromPage;
        if (fromPage >= 0) viewAnim.set(1f);
        else viewAnim.animateTo(1f);
    }

    private void closeSettings() {
        UiContext.unfocus(UiContext.focused());
        inSettings = false;
        if (returnPage >= 0) {
            int target = returnPage;
            returnPage = -1;
            viewAnim.set(0f);
            screen.showPage(target);
            return;
        }
        viewAnim.animateTo(0f);
    }

    @Override
    public boolean back() {
        if (inSettings) {
            closeSettings();
            return true;
        }
        if (!search.getText().isEmpty()) {
            search.setText("");
            query = "";
            refilter();
            return true;
        }
        return false;
    }

    @Override
    public void onShow() {
        grid.scrollToTop();
    }

    // ------------------------------------------------------------------ layout & render

    @Override
    public void layout(float x, float y, float width, float height) {
        super.layout(x, y, width, height);
        settingsView.layout(x, y, width, height);
    }

    private void layoutChips(float startX, float startY) {
        float cx = startX;
        for (int i = 0; i < chipLabels.length; i++) {
            chipW[i] = TextRender.width(chipLabels[i]) + 14;
            chipX[i] = cx;
            cx += chipW[i] + 4;
        }
        chipsY = startY;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float v = viewAnim.get();
        var m = ctx.getMatrices();
        if (v < 0.999f) {
            m.pushMatrix();
            m.translate(-30 * v, 0);
            Render2D.pushAlpha(1f - v);
            double gmx = v > 0.01f ? -1e4 : mx;
            renderGrid(ctx, gmx, my);
            Render2D.popAlpha();
            m.popMatrix();
        }
        if (v > 0.001f) {
            m.pushMatrix();
            m.translate(30 * (1f - v), 0);
            Render2D.pushAlpha(v);
            settingsView.render(ctx, v > 0.99f ? mx : -1e4, my);
            Render2D.popAlpha();
            m.popMatrix();
        }
    }

    private void renderGrid(DrawContext ctx, double mx, double my) {
        float chipsTotal = 0;
        for (String l : chipLabels) chipsTotal += TextRender.width(l) + 18;
        boolean twoRows = width - chipsTotal < 140;
        float searchW = twoRows ? width : Math.max(140, width - chipsTotal - 8);
        search.setBounds(x, y, searchW, 18);
        search.render(ctx, mx, my);
        layoutChips(twoRows ? x : x + searchW + 8, twoRows ? y + 24 : y + 1);

        int sel = category + 1;
        float ix = chipIndicatorX.update(chipX[sel]), iw = chipIndicatorW.update(chipW[sel]);
        if (chipIndicatorW.get() < 1) {
            chipIndicatorX.snap(chipX[sel]);
            chipIndicatorW.snap(chipW[sel]);
        }
        Render2D.roundedRect(ctx, ix, chipsY, iw, 16, 8, Theme.accentSoft());
        Render2D.roundedOutline(ctx, ix, chipsY, iw, 16, 8, 1f, ColorUtil.withAlpha(Theme.accent(), 0x90));
        for (int i = 0; i < chipLabels.length; i++) {
            boolean hov = MathUtil.inside(mx, my, chipX[i], chipsY, chipW[i], 16) && UiContext.inClip(mx, my);
            float h = chipHover[i].update(hov ? 1f : 0f);
            if (i != sel && h > 0.01f) Render2D.roundedRect(ctx, chipX[i], chipsY, chipW[i], 16, 8, ColorUtil.withAlpha(0xFFFFFFFF, (int) (14 * h)));
            int c = i == sel ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, h);
            TextRender.drawCentered(ctx, chipLabels[i], chipX[i] + chipW[i] / 2f, TextRender.centerY(chipsY, 16), c);
        }

        float gy = (twoRows ? chipsY + 16 : y + 18) + 8;
        grid.setBounds(x, gy, width, y + height - gy);
        grid.render(ctx, mx, my);
        if (visible.isEmpty()) {
            float cy = gy + 40;
            IconRender.drawCentered(ctx, Icon.SEARCH, x + width / 2f, cy, 20, Theme.TEXT_DIM);
            TextRender.drawCentered(ctx, "No modules match your search", x + width / 2f, cy + 18, Theme.TEXT_MUTED);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inSettings) return viewAnim.get() > 0.9f && settingsView.mouseClicked(mx, my, button);
        if (viewAnim.get() > 0.05f) return false;
        if (search.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            for (int i = 0; i < chipLabels.length; i++) {
                if (MathUtil.inside(mx, my, chipX[i], chipsY, chipW[i], 16)) {
                    if (category != i - 1) {
                        category = i - 1;
                        Button.playClick();
                        refilter();
                    }
                    return true;
                }
            }
        }
        return grid.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (inSettings) return settingsView.mouseReleased(mx, my, button);
        return grid.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (inSettings) return settingsView.mouseScrolled(mx, my, amount);
        return grid.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (inSettings) return settingsView.keyPressed(key, scancode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inSettings) return settingsView.charTyped(chr, modifiers);
        // start typing anywhere on the grid to search
        if (chr > ' ' && !UiContext.capturesKeyboard()) {
            search.focus();
            return search.charTyped(chr, modifiers);
        }
        return false;
    }

    /** Grid layout with animated reflow. */
    private final class Grid extends ScrollPanel {
        Grid() {
            padding(2);
        }

        @Override
        protected float layoutChildren(float originX, float originY, float contentWidth) {
            int cols = Math.max(1, (int) ((contentWidth + GRID_GAP) / (MIN_CARD_W + GRID_GAP)));
            float cw = (contentWidth - GRID_GAP * (cols - 1)) / cols;
            int i = 0;
            for (Component c : children) {
                int col = i % cols, row = i / cols;
                ((ModuleCard) c).place(originX, originY, col * (cw + GRID_GAP), row * (ModuleCard.HEIGHT + GRID_GAP), cw);
                i++;
            }
            int rows = (children.size() + cols - 1) / cols;
            return rows == 0 ? 0 : rows * (ModuleCard.HEIGHT + GRID_GAP) - GRID_GAP;
        }
    }
}
