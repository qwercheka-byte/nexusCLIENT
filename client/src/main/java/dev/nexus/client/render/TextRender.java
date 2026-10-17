package dev.nexus.client.render;

import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.Mc;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/** Text drawing with sub-pixel positions and the global alpha of {@link Render2D}. */
public final class TextRender {
    private TextRender() {}

    public static TextRenderer font() {
        return Mc.get().textRenderer;
    }

    public static int width(String s) {
        return font().getWidth(s);
    }

    public static int width(Text t) {
        return font().getWidth(t);
    }

    /** Height of a line of text (glyphs occupy ~8px of it). */
    public static int lineHeight() {
        return font().fontHeight;
    }

    /** Y offset that vertically centres a text line inside a box of the given height. */
    public static float centerY(float boxY, float boxHeight) {
        return boxY + (boxHeight - 8f) / 2f;
    }

    private static int prepare(int color) {
        int c = Render2D.apply(color);
        // skip nearly invisible text (vanilla drops alpha 0 itself)
        return ColorUtil.alpha(c) < 5 ? 0 : c;
    }

    /**
     * Records text at a sub-pixel position: the fractional offset goes into the pose, so text keeps the same
     * smooth movement it had with the immediate-mode renderer before 1.21.6.
     */
    private static void submit(DrawContext ctx, OrderedText text, float x, float y, int color, boolean shadow) {
        Matrix3x2f pose = new Matrix3x2f(ctx.getMatrices()).translate(x, y);
        ctx.state.addText(new TextGuiElementRenderState(font(), text, pose, 0, 0, color, 0, shadow, false,
                ctx.scissorStack.peekLast()));
    }

    public static void draw(DrawContext ctx, String s, float x, float y, int color) {
        draw(ctx, s, x, y, color, false);
    }

    public static void draw(DrawContext ctx, String s, float x, float y, int color, boolean shadow) {
        int c = prepare(color);
        if (c == 0 || s.isEmpty()) return;
        submit(ctx, Language.getInstance().reorder(StringVisitable.plain(s)), x, y, c, shadow);
    }

    public static void draw(DrawContext ctx, Text t, float x, float y, int color, boolean shadow) {
        int c = prepare(color);
        if (c == 0) return;
        submit(ctx, t.asOrderedText(), x, y, c, shadow);
    }

    public static void draw(DrawContext ctx, OrderedText t, float x, float y, int color, boolean shadow) {
        int c = prepare(color);
        if (c == 0) return;
        submit(ctx, t, x, y, c, shadow);
    }

    public static void drawCentered(DrawContext ctx, String s, float centerX, float y, int color) {
        draw(ctx, s, centerX - width(s) / 2f, y, color, false);
    }

    public static void drawRight(DrawContext ctx, String s, float rightX, float y, int color) {
        draw(ctx, s, rightX - width(s), y, color, false);
    }

    public static void drawScaled(DrawContext ctx, String s, float x, float y, float scale, int color, boolean shadow) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        draw(ctx, s, 0, 0, color, shadow);
        m.popMatrix();
    }

    public static void drawScaled(DrawContext ctx, Text t, float x, float y, float scale, int color, boolean shadow) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        draw(ctx, t, 0, 0, color, shadow);
        m.popMatrix();
    }

    /** Trims the string to {@code maxWidth} adding an ellipsis. Allocates – cache the result. */
    public static String ellipsize(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        int ell = width("…");
        if (maxWidth <= ell) return "";
        return font().trimToWidth(s, maxWidth - ell) + "…";
    }
}
