package dev.nexus.client.render;

import dev.nexus.client.NexusClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Draws tinted icons from the linear-filtered icon atlas at any size. */
public final class IconRender {
    public static final Identifier ATLAS = Identifier.of(NexusClient.MOD_ID, "textures/gui/icons.png");

    private IconRender() {}

    public static void draw(DrawContext ctx, Icon icon, float x, float y, float size, int color) {
        int c = Render2D.apply(color);
        if ((c >>> 24) < 4) return;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        float s = size / Icon.GLYPH;
        m.scale(s, s);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ATLAS, 0, 0, icon.u(), icon.v(), Icon.GLYPH, Icon.GLYPH, Icon.GLYPH, Icon.GLYPH,
                Icon.ATLAS_WIDTH, Icon.ATLAS_HEIGHT, c);
        m.popMatrix();
    }

    /** Draws the icon centred on (cx, cy). */
    public static void drawCentered(DrawContext ctx, Icon icon, float cx, float cy, float size, int color) {
        draw(ctx, icon, cx - size / 2f, cy - size / 2f, size, color);
    }
}
