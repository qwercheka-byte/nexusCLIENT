package dev.nexus.client.ui;

import dev.nexus.client.util.Lang;
import dev.nexus.client.Nexus;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.component.Component;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Per-screen UI state shared by components: keyboard focus, active drag, clip rectangles for hit testing
 * and the tooltip of the current frame. Render-thread only.
 */
public final class UiContext {
    private static Component focused;
    private static Component dragging;
    private static final float[] CLIP = new float[64 * 4];
    private static int clipDepth;

    private static String tooltip;
    private static double tipX, tipY;
    private static String hoveredTip;
    private static long hoverSince;
    private static String wrappedFor;
    private static List<OrderedText> wrapped;

    private UiContext() {}

    public static void beginFrame() {
        dev.nexus.client.ui.animation.AnimationClock.beginFrame();
        clipDepth = 0;
        tooltip = null;
    }

    public static void reset() {
        focused = null;
        dragging = null;
        clipDepth = 0;
        tooltip = null;
        hoveredTip = null;
    }

    // ------------------------------------------------------------------ focus & drag

    public static void focus(Component c) { focused = c; }
    public static void unfocus(Component c) { if (focused == c) focused = null; }
    public static boolean isFocused(Component c) { return focused == c; }
    public static Component focused() { return focused; }
    /** True while a text field or key listener captures the keyboard. */
    public static boolean capturesKeyboard() { return focused != null; }

    public static void startDrag(Component c) { dragging = c; }
    public static void stopDrag() { dragging = null; }
    public static Component dragging() { return dragging; }

    // ------------------------------------------------------------------ clipping (hit tests)

    public static void pushClip(float x, float y, float w, float h) {
        if (clipDepth >= CLIP.length / 4) return;
        float x2 = x + w, y2 = y + h;
        if (clipDepth > 0) {
            int p = (clipDepth - 1) * 4;
            x = Math.max(x, CLIP[p]);
            y = Math.max(y, CLIP[p + 1]);
            x2 = Math.min(x2, CLIP[p + 2]);
            y2 = Math.min(y2, CLIP[p + 3]);
        }
        int i = clipDepth * 4;
        CLIP[i] = x;
        CLIP[i + 1] = y;
        CLIP[i + 2] = x2;
        CLIP[i + 3] = y2;
        clipDepth++;
    }

    public static void popClip() {
        if (clipDepth > 0) clipDepth--;
    }

    public static boolean inClip(double mx, double my) {
        if (clipDepth == 0) return true;
        int p = (clipDepth - 1) * 4;
        return mx >= CLIP[p] && my >= CLIP[p + 1] && mx < CLIP[p + 2] && my < CLIP[p + 3];
    }

    // ------------------------------------------------------------------ tooltip

    public static void tooltip(String text, double mx, double my) {
        if (text == null || text.isEmpty()) return;
        tooltip = text;
        tipX = mx;
        tipY = my;
    }

    /** Draws the tooltip requested this frame after a short hover delay. */
    public static void renderTooltip(DrawContext ctx, float maxX, float maxY) {
        if (tooltip == null || !Nexus.settings().tooltips.isOn()) {
            hoveredTip = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (!tooltip.equals(hoveredTip)) {
            hoveredTip = tooltip;
            hoverSince = now;
        }
        long shown = now - hoverSince - 450;
        if (shown < 0) return;
        if (!tooltip.equals(wrappedFor)) {
            wrappedFor = tooltip;
            wrapped = TextRender.font().wrapLines(Text.literal(Lang.tr(tooltip)), 180);
        }
        float w = 0;
        for (OrderedText line : wrapped) w = Math.max(w, TextRender.font().getWidth(line));
        w += 12;
        float h = wrapped.size() * 10 + 8;
        float x = (float) tipX + 10, y = (float) tipY + 12;
        if (x + w > maxX - 4) x = (float) tipX - w - 6;
        if (y + h > maxY - 4) y = (float) tipY - h - 6;
        float a = Math.min(1f, shown / 120f);
        Render2D.pushAlpha(a);
        Render2D.shadow(ctx, x, y + 1, w, h, 5, 5, 0x60000000);
        Render2D.roundedRect(ctx, x, y, w, h, 5, 0xF51A1B22);
        Render2D.roundedOutline(ctx, x, y, w, h, 5, 1, Theme.BORDER_STRONG);
        float ty = y + 5;
        for (OrderedText line : wrapped) {
            int c = Render2D.apply(Theme.TEXT_MUTED);
            if ((c >>> 24) >= 5) {
                TextRender.draw(ctx, line, x + 6, ty, c, false);
            }
            ty += 10;
        }
        Render2D.popAlpha();
    }
}
