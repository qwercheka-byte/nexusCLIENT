package dev.nexus.client.render;

import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.Lang;
import dev.nexus.client.util.Mc;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/** Text drawing with sub-pixel positions and the global alpha of {@link Render2D}. */
public final class TextRender {
    /** Modern bundled UI typeface (Inter, declared in assets/nexus/font/ui.json). */
    public static final Identifier UI_FONT = Identifier.of("nexus", "ui");
    private static final Style UI_STYLE = Style.EMPTY.withFont(new net.minecraft.text.StyleSpriteSource.Font(UI_FONT));
    /** The bundled font is authored at 20px for crispness and always drawn at half scale. */
    public static final float UI_SCALE = 0.5f;

    private TextRender() {}

    public static TextRenderer font() {
        return Mc.get().textRenderer;
    }

    /** Width of a UI string (translated, see {@link Lang}), measured in the UI typeface. */
    public static int width(String s) {
        return widthRaw(Lang.tr(s));
    }

    /** Width of a string exactly as given (user input, names) – no translation. */
    public static int widthRaw(String s) {
        return (int) Math.ceil(font().getWidth(StringVisitable.styled(s, UI_STYLE)) * UI_SCALE);
    }

    public static int width(Text t) {
        return (int) Math.ceil(font().getWidth(t) * UI_SCALE);
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
    /** Forces the UI typeface on every glyph that does not pick a font itself (covers Text/OrderedText callers). */
    private static OrderedText withUiFont(OrderedText in) {
        return sink -> in.accept((index, style, codePoint) ->
                sink.accept(index, UI_STYLE.withParent(style), codePoint));
    }

    private static void submit(DrawContext ctx, OrderedText text, float x, float y, int color, boolean shadow) {
        text = withUiFont(text);
        Matrix3x2f pose = new Matrix3x2f(ctx.getMatrices()).translate(x, y).scale(UI_SCALE, UI_SCALE);
        ctx.state.addText(new TextGuiElementRenderState(font(), text, pose, 0, 0, color, 0, shadow, false,
                ctx.scissorStack.peekLast()));
    }

    public static void draw(DrawContext ctx, String s, float x, float y, int color) {
        draw(ctx, s, x, y, color, false);
    }

    /** Draws a UI string, translated to the game language (see {@link Lang}). */
    public static void draw(DrawContext ctx, String s, float x, float y, int color, boolean shadow) {
        drawRaw(ctx, Lang.tr(s), x, y, color, shadow);
    }

    /** Draws a string exactly as given (user input, names) – no translation. */
    public static void drawRaw(DrawContext ctx, String s, float x, float y, int color, boolean shadow) {
        int c = prepare(color);
        if (c == 0 || s.isEmpty()) return;
        submit(ctx, Language.getInstance().reorder(StringVisitable.styled(s, UI_STYLE)), x, y, c, shadow);
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

    /** Centered, without translation. */
    public static void drawCenteredRaw(DrawContext ctx, String s, float centerX, float y, int color) {
        drawRaw(ctx, s, centerX - widthRaw(s) / 2f, y, color, false);
    }

    /** Scaled, without translation. */
    public static void drawScaledRaw(DrawContext ctx, String s, float x, float y, float scale, int color, boolean shadow) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        drawRaw(ctx, s, 0, 0, color, shadow);
        m.popMatrix();
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

    /** Translates the UI string and trims it to {@code maxWidth} adding an ellipsis. Allocates – cache the result. */
    public static String ellipsize(String s, int maxWidth) {
        return ellipsizeRaw(Lang.tr(s), maxWidth);
    }

    /** Trims the string (not translated) to {@code maxWidth} adding an ellipsis. */
    public static String ellipsizeRaw(String s, int maxWidth) {
        if (widthRaw(s) <= maxWidth) return s;
        int ell = width("…");
        if (maxWidth <= ell) return "";
        int budget = maxWidth - ell;
        // binary search the longest UI-font prefix that fits
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (widthRaw(s.substring(0, mid)) <= budget) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "…";
    }
}
