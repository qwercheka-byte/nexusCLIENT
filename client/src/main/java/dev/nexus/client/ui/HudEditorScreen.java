package dev.nexus.client.ui;

import dev.nexus.client.ui.animation.AnimationClock;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import dev.nexus.client.util.Mc;
import dev.nexus.client.ClientSettings;
import dev.nexus.client.Nexus;
import dev.nexus.client.hud.HudModule;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.animation.Easing;
import dev.nexus.client.ui.animation.Fade;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drag-and-drop HUD layout editor with snapping (screen edges, centre, other elements, grid),
 * guide lines, corner-handle and mouse-wheel scaling, keyboard nudging, reset and preview mode.
 * Positions are stored per profile through {@link HudModule#setPosition}.
 */
public final class HudEditorScreen extends Screen {
    private static final float SNAP = 4f, HANDLE = 5f, TOOL_H = 20;

    private final Screen parent;
    private final Fade fade = new Fade(200, 150, Easing.OUT_CUBIC);
    private final long openedAt = AnimationClock.millis();

    private HudModule selected, hovered, dragging, resizing;
    private float dragOffX, dragOffY;
    private int resizeCorner; // 0 TL, 1 TR, 2 BR, 3 BL
    private float anchorCornerX, anchorCornerY;
    private boolean moved;
    private boolean preview;
    private boolean elementsOpen;
    private long lastInteraction = AnimationClock.millis();

    // guides of the current drag (screen coordinates, NaN = none)
    private float guideX = Float.NaN, guideY = Float.NaN;
    /** Reused buffer of snap lines (no allocation while dragging). */
    private float[] snapTargets = new float[48];

    private final List<Tool> tools = new ArrayList<>();
    private final SmoothValue elementsAnim = new SmoothValue(0f, 16f);
    private final SmoothValue toolbarAlpha = new SmoothValue(1f, 12f);
    private final SmoothValue selectionAnim = new SmoothValue(0f, 18f);
    private float toolX, toolY, toolW;
    private String selectionLabel = "";
    private HudModule labelFor;
    private double labelScale = Double.NaN;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("NEXUS HUD Editor"));
        this.parent = parent;
        ClientSettings s = Nexus.settings();
        tools.add(new Tool(Icon.LAYOUT, "Elements", () -> elementsOpen, () -> elementsOpen = !elementsOpen));
        tools.add(new Tool(Icon.GRID, "Grid", s.editorShowGrid::isOn, s.editorShowGrid::toggle));
        tools.add(new Tool(Icon.MAGNET, "Snap", s.editorSnap::isOn, s.editorSnap::toggle));
        tools.add(new Tool(Icon.EYE, "Preview", () -> preview, this::togglePreview));
        tools.add(new Tool(Icon.RESET, "Reset", () -> false, this::resetSelectedOrAll));
        tools.add(new Tool(Icon.CHECK, "Done", () -> false, this::close));
        fade.restartIn();
    }

    private void togglePreview() {
        preview = !preview;
        elementsOpen = false;
        if (preview) selected = null;
    }

    private void resetSelectedOrAll() {
        if (selected != null) {
            selected.resetLayout();
        } else {
            resetAll();
        }
    }

    private void resetAll() {
        for (HudModule m : Nexus.modules().hudModules()) m.resetLayout();
        Nexus.notifications().post("HUD reset", "All elements moved to defaults", dev.nexus.client.hud.notification.Notification.Type.INFO);
    }

    @Override
    public void close() {
        UiContext.reset();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        Nexus.config().flushAsync();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // the world stays visible behind the editor
    }

    // ------------------------------------------------------------------ geometry helpers

    private float ex(HudModule m) { return m.getX(width); }
    private float ey(HudModule m) { return m.getY(height); }

    private HudModule elementAt(double mx, double my) {
        List<HudModule> list = Nexus.modules().hudModules();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (!m.isEnabled()) continue;
            if (MathUtil.inside(mx, my, ex(m) - 1, ey(m) - 1, m.scaledWidth() + 2, m.scaledHeight() + 2)) return m;
        }
        return null;
    }

    /** Corner i (0 TL, 1 TR, 2 BR, 3 BL) of an element – computed, not stored, so nothing is allocated per frame. */
    private static float cornerX(float x, float w, int i) { return i == 1 || i == 2 ? x + w : x; }
    private static float cornerY(float y, float h, int i) { return i >= 2 ? y + h : y; }

    private int cornerAt(HudModule m, double mx, double my) {
        float x = ex(m), y = ey(m), w = m.scaledWidth(), h = m.scaledHeight();
        for (int i = 0; i < 4; i++) {
            if (Math.abs(mx - cornerX(x, w, i)) <= HANDLE && Math.abs(my - cornerY(y, h, i)) <= HANDLE) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Render2D.resetAlpha();
        UiContext.beginFrame();
        float f = fade.alpha();
        ClientSettings s = Nexus.settings();

        if (!preview) {
            Render2D.rect(ctx, 0, 0, width, height, ColorUtil.withAlpha(0xFF08080C, (int) (70 * f)));
            if (s.editorShowGrid.isOn()) drawGrid(ctx, s.editorGrid.getInt(), f);
            // centre cross
            Render2D.rect(ctx, width / 2f - 0.5f, 0, 1, height, ColorUtil.withAlpha(Theme.accent(), (int) (28 * f)));
            Render2D.rect(ctx, 0, height / 2f - 0.5f, width, 1, ColorUtil.withAlpha(Theme.accent(), (int) (28 * f)));
        }

        hovered = dragging != null || resizing != null ? null : elementAt(mouseX, mouseY);
        if (isOverUi(mouseX, mouseY)) hovered = null;

        List<HudModule> list = Nexus.modules().hudModules();
        for (int li = 0; li < list.size(); li++) {
            HudModule m = list.get(li);
            if (!m.isEnabled()) continue;
            Nexus.hud().renderElement(ctx, m, !preview, width, height);
            if (preview) continue;
            float x = ex(m), y = ey(m), w = m.scaledWidth(), h = m.scaledHeight();
            if (m != selected) {
                int outline = m == hovered ? 0x90FFFFFF : 0x30FFFFFF;
                Render2D.roundedOutline(ctx, x - 1, y - 1, w + 2, h + 2, 2, 1f, outline);
            }
        }

        if (!preview && selected != null && selected.isEnabled()) drawSelection(ctx, mouseX, mouseY);
        else selectionAnim.snap(0f);

        if (!Float.isNaN(guideX)) Render2D.rect(ctx, guideX - 0.5f, 0, 1, height, Theme.accentBright());
        if (!Float.isNaN(guideY)) Render2D.rect(ctx, 0, guideY - 0.5f, width, 1, Theme.accentBright());

        if (preview) {
            float a = 0.25f + 0.75f * AnimationClock.holdThenFade(lastInteraction, 1500, 600);
            Render2D.pushAlpha(a);
            String hint = "Preview · press P to return to editing";
            float w = TextRender.width(hint) + 16;
            Render2D.roundedRect(ctx, (width - w) / 2f, 6, w, 16, 8, 0xD0111117);
            TextRender.drawCentered(ctx, hint, width / 2f, 10, Theme.TEXT_MUTED);
            Render2D.popAlpha();
        } else {
            drawHelp(ctx);
            drawToolbar(ctx, mouseX, mouseY);
        }
        UiContext.renderTooltip(ctx, width, height);
        Nexus.notifications().render(ctx, width, height);
    }

    private void drawGrid(DrawContext ctx, int step, float f) {
        int c = ColorUtil.withAlpha(0xFFFFFFFF, (int) (12 * f));
        for (int x = step; x < width; x += step) Render2D.rect(ctx, x, 0, 1, height, c);
        for (int y = step; y < height; y += step) Render2D.rect(ctx, 0, y, width, 1, c);
    }

    private void drawSelection(DrawContext ctx, int mx, int my) {
        HudModule m = selected;
        float a = selectionAnim.update(1f);
        float x = ex(m), y = ey(m), w = m.scaledWidth(), h = m.scaledHeight();
        Render2D.pushAlpha(a);
        Render2D.rect(ctx, x - 1, y - 1, w + 2, h + 2, ColorUtil.withAlpha(Theme.accent(), 0x18));
        Render2D.roundedOutline(ctx, x - 1.5f, y - 1.5f, w + 3, h + 3, 2, 1f, Theme.accent());
        int hoverCorner = resizing != null ? resizeCorner : cornerAt(m, mx, my);
        for (int i = 0; i < 4; i++) {
            float r = i == hoverCorner ? 3.2f : 2.4f;
            float cx = cornerX(x, w, i), cy = cornerY(y, h, i);
            Render2D.circle(ctx, cx, cy, r + 0.8f, 0xFF111117);
            Render2D.circle(ctx, cx, cy, r, i == hoverCorner ? 0xFFFFFFFF : Theme.accentBright());
        }
        if (m != labelFor || m.getScale() != labelScale) {
            labelFor = m;
            labelScale = m.getScale();
            selectionLabel = m.getName() + "  " + Math.round(labelScale * 100) + "%";
        }
        float lw = TextRender.widthRaw(selectionLabel) + 10;
        float lx = MathUtil.clamp(x + w / 2f - lw / 2f, 2, width - lw - 2);
        float ly = y - 16 >= 2 ? y - 16 : y + h + 4;
        Render2D.roundedRect(ctx, lx, ly, lw, 12, 6, Theme.accent());
        TextRender.drawScaledRaw(ctx, selectionLabel, lx + 5, ly + 2.5f, 0.85f, Theme.onAccent(), false);
        Render2D.popAlpha();
    }

    private void drawHelp(DrawContext ctx) {
        float a = AnimationClock.holdThenFade(openedAt, 5000, 800);
        if (a <= 0.01f) return;
        Render2D.pushAlpha(a);
        String line = "Drag to move · Wheel / corners to scale · Shift: no snap · Arrows nudge · R reset · Del hide · Right-click: settings";
        String shown = TextRender.ellipsize(line, width - 30);
        float w = TextRender.width(shown) + 16;
        Render2D.roundedRect(ctx, (width - w) / 2f, 6, w, 16, 8, 0xD0111117);
        Render2D.roundedOutline(ctx, (width - w) / 2f, 6, w, 16, 8, 1f, Theme.BORDER);
        TextRender.drawCentered(ctx, shown, width / 2f, 10, Theme.TEXT_MUTED);
        Render2D.popAlpha();
    }

    private void layoutToolbar() {
        boolean iconsOnly = width < 330;
        float total = 0;
        for (int ti = 0; ti < tools.size(); ti++) {
            Tool t = tools.get(ti);
            t.w = iconsOnly ? 22 : TextRender.width(t.label) + 28;
            total += t.w + 2;
        }
        toolW = total + 4;
        toolX = (width - toolW) / 2f;
        toolY = height - TOOL_H - 34; // above the hotbar
        float cx = toolX + 3;
        for (int ti = 0; ti < tools.size(); ti++) {
            Tool t = tools.get(ti);
            t.x = cx;
            t.iconsOnly = iconsOnly;
            cx += t.w + 2;
        }
    }

    private boolean isOverUi(double mx, double my) {
        if (preview) return false;
        layoutToolbar();
        if (MathUtil.inside(mx, my, toolX, toolY, toolW, TOOL_H + 4)) return true;
        return elementsOpen && MathUtil.inside(mx, my, elementsX(), elementsY(), 150, elementsH());
    }

    private float elementsH() { return Nexus.modules().hudModules().size() * 16 + 8; }
    private float elementsX() { return tools.get(0).x; }
    private float elementsY() { return toolY - elementsH() - 6; }

    private void drawToolbar(DrawContext ctx, int mx, int my) {
        layoutToolbar();
        boolean busy = dragging != null || resizing != null;
        float ta = toolbarAlpha.update(busy ? 0.2f : 1f);
        Render2D.pushAlpha(ta);
        Render2D.shadow(ctx, toolX, toolY + 2, toolW, TOOL_H + 4, 8, 8, 0x70000000);
        Render2D.roundedRect(ctx, toolX, toolY, toolW, TOOL_H + 4, 8, 0xF0141419);
        Render2D.roundedOutline(ctx, toolX, toolY, toolW, TOOL_H + 4, 8, 1f, Theme.BORDER);
        for (int ti = 0; ti < tools.size(); ti++) tools.get(ti).render(ctx, mx, my, !busy);

        float e = elementsAnim.update(elementsOpen ? 1f : 0f);
        if (e > 0.01f) {
            float px = elementsX(), py = elementsY() + (1f - e) * 6, ph = elementsH();
            Render2D.pushAlpha(e);
            Render2D.roundedRect(ctx, px, py, 150, ph, 6, 0xF5141419);
            Render2D.roundedOutline(ctx, px, py, 150, ph, 6, 1f, Theme.BORDER);
            float ry = py + 4;
            List<HudModule> hudList = Nexus.modules().hudModules();
            for (int li = 0; li < hudList.size(); li++) {
                HudModule m = hudList.get(li);
                boolean hov = !busy && MathUtil.inside(mx, my, px + 3, ry, 144, 16);
                if (hov) Render2D.roundedRect(ctx, px + 3, ry, 144, 16, 4, 0x14FFFFFF);
                Render2D.roundedRect(ctx, px + 8, ry + 4, 8, 8, 2, m.isEnabled() ? Theme.accent() : 0xFF2A2B36);
                if (m.isEnabled()) IconRender.drawCentered(ctx, Icon.CHECK, px + 12, ry + 8, 6, Theme.onAccent());
                TextRender.drawRaw(ctx, TextRender.ellipsizeRaw(m.getName(), 120), px + 22, ry + 4, m.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM, false);
                ry += 16;
            }
            Render2D.popAlpha();
        }
        Render2D.popAlpha();
    }

    // ------------------------------------------------------------------ input

    private boolean onMouseClicked(double mx, double my, int button) {
        lastInteraction = AnimationClock.millis();
        if (preview) {
            return false;
        }
        layoutToolbar();
        if (elementsOpen && MathUtil.inside(mx, my, elementsX(), elementsY(), 150, elementsH())) {
            float ry = elementsY() + 4;
            List<HudModule> hudList = Nexus.modules().hudModules();
            for (int li = 0; li < hudList.size(); li++) {
                HudModule m = hudList.get(li);
                if (MathUtil.inside(mx, my, elementsX() + 3, ry, 144, 16)) {
                    m.toggle();
                    Button.playClick();
                    if (!m.isEnabled() && selected == m) selected = null;
                    return true;
                }
                ry += 16;
            }
            return true;
        }
        for (int ti = 0; ti < tools.size(); ti++) {
            Tool t = tools.get(ti);
            if (MathUtil.inside(mx, my, t.x, toolY + 2, t.w, TOOL_H)) {
                Button.playClick();
                t.action.run();
                return true;
            }
        }
        if (MathUtil.inside(mx, my, toolX, toolY, toolW, TOOL_H + 4)) return true;
        elementsOpen = false;

        // corner handles of the selection take priority
        if (button == 0 && selected != null && selected.isEnabled()) {
            int corner = cornerAt(selected, mx, my);
            if (corner >= 0) {
                resizing = selected;
                resizeCorner = corner;
                float x = ex(selected), y = ey(selected), w = selected.scaledWidth(), h = selected.scaledHeight();
                // the opposite corner stays fixed
                anchorCornerX = (corner == 1 || corner == 2) ? x : x + w;
                anchorCornerY = (corner == 2 || corner == 3) ? y : y + h;
                return true;
            }
        }

        HudModule hit = elementAt(mx, my);
        if (hit == null) {
            selected = null;
            return false;
        }
        selected = hit;
        if (button == 1) {
            openSettings(hit);
            return true;
        }
        if (button == 0) {
            dragging = hit;
            moved = false;
            dragOffX = (float) mx - ex(hit);
            dragOffY = (float) my - ey(hit);
        }
        return true;
    }

    private void openSettings(HudModule m) {
        if (client == null) return;
        NexusScreen menu = parent instanceof NexusScreen n ? n : new NexusScreen(NexusScreen.MODULES);
        UiContext.reset();
        client.setScreen(menu);
        menu.openModuleSettings(m);
    }

    private boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
        lastInteraction = AnimationClock.millis();
        boolean free = Mc.shiftDown() || !Nexus.settings().editorSnap.isOn();
        if (dragging != null) {
            moved = true;
            float w = dragging.scaledWidth(), h = dragging.scaledHeight();
            float nx = (float) mx - dragOffX, ny = (float) my - dragOffY;
            guideX = Float.NaN;
            guideY = Float.NaN;
            if (!free) {
                nx = snapAxis(nx, w, width, true);
                ny = snapAxis(ny, h, height, false);
            }
            dragging.setPosition(nx, ny, width, height);
            return true;
        }
        if (resizing != null) {
            float baseW = resizing.scaledWidth() / resizing.getScale();
            float baseH = resizing.scaledHeight() / resizing.getScale();
            if (baseW <= 0 || baseH <= 0) return true;
            float dxm = Math.abs((float) mx - anchorCornerX), dym = Math.abs((float) my - anchorCornerY);
            double target = Math.max(dxm / baseW, dym / baseH);
            var setting = resizing.scaleSetting();
            target = MathUtil.clamp(target, setting.getMin(), setting.getMax());
            setting.set(target);
            float w = resizing.scaledWidth(), h = resizing.scaledHeight();
            float nx = (resizeCorner == 1 || resizeCorner == 2) ? anchorCornerX : anchorCornerX - w;
            float ny = (resizeCorner == 2 || resizeCorner == 3) ? anchorCornerY : anchorCornerY - h;
            resizing.setPosition(nx, ny, width, height);
            return true;
        }
        return false;
    }

    /** Snaps one axis to screen edges / centre, other elements and finally the grid; sets the guide. */
    private float snapAxis(float pos, float size, float screen, boolean horizontal) {
        float best = Float.NaN, bestDist = SNAP + 0.001f, guide = Float.NaN;
        // snap lines: screen start / centre / end, then start / centre / end of every other visible element
        int n = 0;
        List<HudModule> modules = Nexus.modules().hudModules();
        if (snapTargets.length < 3 + modules.size() * 3) snapTargets = Arrays.copyOf(snapTargets, 3 + modules.size() * 3);
        snapTargets[n++] = 0f;
        snapTargets[n++] = screen / 2f;
        snapTargets[n++] = screen;
        for (int k = 0; k < modules.size(); k++) {
            HudModule m = modules.get(k);
            if (m == dragging || !m.isEnabled()) continue;
            float s = horizontal ? ex(m) : ey(m);
            float l = horizontal ? m.scaledWidth() : m.scaledHeight();
            snapTargets[n++] = s;
            snapTargets[n++] = s + l / 2f;
            snapTargets[n++] = s + l;
        }
        // candidate edges of the dragged element: start (0), centre (1), end (2)
        for (int i = 0; i < 3; i++) {
            float own = pos + size * i / 2f;
            for (int j = 0; j < n; j++) {
                float t = snapTargets[j];
                float d = Math.abs(own - t);
                if (d < bestDist) {
                    bestDist = d;
                    best = pos + (t - own);
                    guide = t;
                }
            }
        }
        if (!Float.isNaN(best)) {
            if (horizontal) guideX = guide;
            else guideY = guide;
            return best;
        }
        if (Nexus.settings().editorShowGrid.isOn()) {
            int step = Nexus.settings().editorGrid.getInt();
            return Math.round(pos / step) * (float) step;
        }
        return pos;
    }

    private boolean onMouseReleased(double mx, double my, int button) {
        // each element stores its own position/scale in its module entry; write the change right away
        if (dragging != null || resizing != null) Nexus.config().flushAsync();
        dragging = null;
        resizing = null;
        guideX = Float.NaN;
        guideY = Float.NaN;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        lastInteraction = AnimationClock.millis();
        if (preview || vertical == 0) return false;
        HudModule target = elementAt(mx, my);
        if (target == null) target = selected;
        if (target == null) return false;
        selected = target;
        var setting = target.scaleSetting();
        double step = Mc.shiftDown() ? setting.getStep() : Math.max(setting.getStep(), 0.05);
        target.setScaleKeepingCenter(MathUtil.clamp(target.getScale() + (vertical > 0 ? step : -step), setting.getMin(), setting.getMax()), width, height);
        return true;
    }

    private boolean onKeyPressed(int key, int scancode, int modifiers) {
        lastInteraction = AnimationClock.millis();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (preview) {
                preview = false;
                return true;
            }
            if (elementsOpen) {
                elementsOpen = false;
                return true;
            }
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_P) {
            togglePreview();
            return true;
        }
        if (preview) return true;
        if (key == GLFW.GLFW_KEY_G) {
            Nexus.settings().editorShowGrid.toggle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_R && Mc.ctrl(modifiers)) {
            resetAll();
            return true;
        }
        if (selected != null) {
            int step = Mc.shift(modifiers) ? 10 : 1;
            float dx = 0, dy = 0;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> dx = -step;
                case GLFW.GLFW_KEY_RIGHT -> dx = step;
                case GLFW.GLFW_KEY_UP -> dy = -step;
                case GLFW.GLFW_KEY_DOWN -> dy = step;
                case GLFW.GLFW_KEY_R -> {
                    selected.resetLayout();
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                    selected.setEnabled(false);
                    selected = null;
                    return true;
                }
                default -> { }
            }
            if (dx != 0 || dy != 0) {
                selected.setPosition(ex(selected) + dx, ey(selected) + dy, width, height);
                return true;
            }
        }
        return super.keyPressed(new KeyInput(key, scancode, modifiers));
    }

    /** Toolbar button. */
    private final class Tool {
        final Icon icon;
        final String label;
        final java.util.function.BooleanSupplier active;
        final Runnable action;
        final SmoothValue hover = new SmoothValue(0f, 18f);
        float x, w;
        boolean iconsOnly;

        Tool(Icon icon, String label, java.util.function.BooleanSupplier active, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.active = active;
            this.action = action;
        }

        void render(DrawContext ctx, int mx, int my, boolean interactive) {
            boolean hov = interactive && MathUtil.inside(mx, my, x, toolY + 2, w, TOOL_H);
            float h = hover.update(hov ? 1f : 0f);
            boolean on = active.getAsBoolean();
            int bg = on ? Theme.accentSoft() : ColorUtil.withAlpha(0xFFFFFFFF, (int) (16 * h));
            if ((bg >>> 24) > 0) Render2D.roundedRect(ctx, x, toolY + 2, w, TOOL_H, 6, bg);
            int fg = on ? Theme.accentBright() : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, h);
            if (iconsOnly) {
                IconRender.drawCentered(ctx, icon, x + w / 2f, toolY + 2 + TOOL_H / 2f, 10, fg);
                if (hov) UiContext.tooltip(label, mx, my);
            } else {
                IconRender.draw(ctx, icon, x + 7, toolY + 2 + (TOOL_H - 10) / 2f, 10, fg);
                TextRender.draw(ctx, label, x + 21, TextRender.centerY(toolY + 2, TOOL_H), fg);
            }
        }
    }

    // ------------------------------------------------------------------ 1.21.9+ input events → internal handlers

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return onMouseClicked(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(Click click) {
        return onMouseReleased(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        return onMouseDragged(click.x(), click.y(), click.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return onKeyPressed(input.key(), input.scancode(), input.modifiers());
    }
}
