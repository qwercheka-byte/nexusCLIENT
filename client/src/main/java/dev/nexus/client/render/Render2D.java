package dev.nexus.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.Mc;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * Vector shape renderer for the GUI: rounded rectangles, outlines, gradients, circles, soft shadows and lines.
 * <p>
 * Since 1.21.6 the GUI is rendered deferred: every draw call records a render state that is turned into
 * vertices later. Each shape here records one small {@link Shape} (parameters + a copy of the pose, the same
 * cost as a vanilla {@code fill}) into {@link DrawContext}'s {@code GuiRenderState}; the geometry itself is
 * generated in {@link Shape#setupVertices} using static scratch buffers (render thread only), so no vertex
 * arrays are allocated. Accurate {@link Shape#bounds()} let vanilla's layering keep draw order intact.
 * Edges get a one-device-pixel alpha feather for cheap anti-aliasing.
 */
public final class Render2D {
    private static final RenderPipeline PIPELINE = RenderPipelines.GUI;
    private static final int MAX_SEGMENTS = 16;
    private static final int MAX_POINTS = 4 * (MAX_SEGMENTS + 1);
    private static final float[] PX = new float[MAX_POINTS], PY = new float[MAX_POINTS];
    private static final float[] NX = new float[MAX_POINTS], NY = new float[MAX_POINTS];
    private static final float[] QX = new float[MAX_POINTS], QY = new float[MAX_POINTS];
    private static final float[] RADII = new float[4], INNER_RADII = new float[4];
    private static final int[] SEGS = new int[4];

    private static final float[] ALPHA_STACK = new float[32];
    private static int alphaDepth;
    private static float alpha = 1f;

    private static final int QUAD = 0, ROUNDED = 1, OUTLINE = 2, SHADOW = 3;

    private Render2D() {}

    // ------------------------------------------------------------------ global alpha

    /** Multiplies every colour drawn until {@link #popAlpha()} by {@code factor}. Nestable. */
    public static void pushAlpha(float factor) {
        if (alphaDepth < ALPHA_STACK.length) ALPHA_STACK[alphaDepth] = alpha;
        alphaDepth++;
        alpha *= Math.max(0f, Math.min(1f, factor));
    }

    public static void popAlpha() {
        if (alphaDepth <= 0) return;
        alphaDepth--;
        alpha = alphaDepth < ALPHA_STACK.length ? ALPHA_STACK[alphaDepth] : 1f;
    }

    public static float alpha() {
        return alpha;
    }

    /** Resets the alpha stack (safety net at the start of a frame). */
    public static void resetAlpha() {
        alphaDepth = 0;
        alpha = 1f;
    }

    public static int apply(int color) {
        return alpha >= 1f ? color : ColorUtil.mulAlpha(color, alpha);
    }

    // ------------------------------------------------------------------ recording

    private static float pixel() {
        return 1f / (float) Mc.get().getWindow().getScaleFactor();
    }

    /**
     * One recorded shape. Generic float slots keep this a single small class:
     * QUAD: a..h = 4 points, c1..c4 colours. ROUNDED/OUTLINE/SHADOW: a..d = x,y,w,h; e..h = radii TL,TR,BR,BL;
     * i = feather / thickness / shadow size; c1, c2 = colours; horizontal = gradient direction.
     */
    private static final class Shape implements SimpleGuiElementRenderState {
        private final int kind;
        private final Matrix3x2fc pose;
        private final float a, b, c, d, e, f, g, h, i;
        private final int c1, c2, c3, c4;
        private final boolean horizontal;
        private final @Nullable ScreenRect scissor;
        private final @Nullable ScreenRect bounds;

        Shape(DrawContext ctx, int kind, float a, float b, float c, float d, float e, float f, float g, float h, float i,
              int c1, int c2, int c3, int c4, boolean horizontal,
              float minX, float minY, float maxX, float maxY) {
            this.kind = kind;
            this.pose = new Matrix3x2f(ctx.getMatrices());
            this.a = a; this.b = b; this.c = c; this.d = d; this.e = e; this.f = f; this.g = g; this.h = h; this.i = i;
            this.c1 = c1; this.c2 = c2; this.c3 = c3; this.c4 = c4;
            this.horizontal = horizontal;
            this.scissor = ctx.scissorStack.peekLast();
            int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY);
            int x1 = (int) Math.ceil(maxX), y1 = (int) Math.ceil(maxY);
            ScreenRect rect = new ScreenRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)).transformEachVertex(pose);
            this.bounds = scissor != null ? scissor.intersection(rect) : rect;
        }

        @Override
        public void setupVertices(VertexConsumer vc) {
            switch (kind) {
                case QUAD -> quad(vc, pose, a, b, c1, c, d, c2, e, f, c3, g, h, c4);
                case ROUNDED -> emitRounded(vc, pose, a, b, c, d, e, f, g, h, c1, c2, horizontal, i);
                case OUTLINE -> emitOutline(vc, pose, a, b, c, d, e, i, c1);
                case SHADOW -> emitShadow(vc, pose, a, b, c, d, e, i, c1);
                default -> { }
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return PIPELINE;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.empty();
        }

        @Override
        public @Nullable ScreenRect scissorArea() {
            return scissor;
        }

        @Override
        public @Nullable ScreenRect bounds() {
            return bounds;
        }
    }

    private static void submit(DrawContext ctx, Shape shape) {
        if (shape.bounds != null && shape.bounds.width() > 0 && shape.bounds.height() > 0) {
            ctx.state.addSimpleElement(shape);
        }
    }

    private static void submitQuad(DrawContext ctx, float x1, float y1, int k1, float x2, float y2, int k2,
                                   float x3, float y3, int k3, float x4, float y4, int k4) {
        float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4)), maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4)), maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
        submit(ctx, new Shape(ctx, QUAD, x1, y1, x2, y2, x3, y3, x4, y4, 0, k1, k2, k3, k4, false, minX, minY, maxX, maxY));
    }

    // ------------------------------------------------------------------ vertex emission (render thread)

    /** Emits a quad, fixing the winding so it is never back-face culled. */
    private static void quad(VertexConsumer vc, Matrix3x2fc m,
                             float x1, float y1, int c1, float x2, float y2, int c2,
                             float x3, float y3, int c3, float x4, float y4, int c4) {
        float cross = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        if (cross <= 0) {
            vc.vertex(m, x1, y1).color(c1);
            vc.vertex(m, x2, y2).color(c2);
            vc.vertex(m, x3, y3).color(c3);
            vc.vertex(m, x4, y4).color(c4);
        } else {
            vc.vertex(m, x1, y1).color(c1);
            vc.vertex(m, x4, y4).color(c4);
            vc.vertex(m, x3, y3).color(c3);
            vc.vertex(m, x2, y2).color(c2);
        }
    }

    private static void tri(VertexConsumer vc, Matrix3x2fc m, float x1, float y1, int c1, float x2, float y2, int c2,
                            float x3, float y3, int c3) {
        quad(vc, m, x1, y1, c1, x2, y2, c2, x3, y3, c3, x3, y3, c3);
    }

    private static void emitRounded(VertexConsumer vc, Matrix3x2fc m, float x, float y, float w, float h,
                                    float tl, float tr, float br, float bl, int c1, int c2, boolean horizontal, float aa) {
        prepareRadii(w, h, tl, tr, br, bl, aa);
        int n = build(x, y, w, h, RADII, SEGS, PX, PY, NX, NY);
        float cx = x + w / 2f, cy = y + h / 2f;
        int cc = colorAt(cx, cy, x, y, w, h, c1, c2, horizontal);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int ci = colorAt(PX[i], PY[i], x, y, w, h, c1, c2, horizontal);
            int cj = colorAt(PX[j], PY[j], x, y, w, h, c1, c2, horizontal);
            tri(vc, m, cx, cy, cc, PX[i], PY[i], ci, PX[j], PY[j], cj);
            if (aa > 0) {
                quad(vc, m, PX[i], PY[i], ci, PX[j], PY[j], cj,
                        PX[j] + NX[j] * aa, PY[j] + NY[j] * aa, cj & 0x00FFFFFF,
                        PX[i] + NX[i] * aa, PY[i] + NY[i] * aa, ci & 0x00FFFFFF);
            }
        }
    }

    private static void emitOutline(VertexConsumer vc, Matrix3x2fc m, float x, float y, float w, float h,
                                    float r, float thickness, int c) {
        float px = pixel();
        prepareRadii(w, h, r, r, r, r, px);
        int n = build(x, y, w, h, RADII, SEGS, PX, PY, NX, NY);
        for (int i = 0; i < 4; i++) INNER_RADII[i] = Math.max(0, RADII[i] - thickness);
        build(x + thickness, y + thickness, w - 2 * thickness, h - 2 * thickness, INNER_RADII, SEGS, QX, QY, null, null);
        float aa = px * 0.75f;
        int transparent = c & 0x00FFFFFF;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            quad(vc, m, PX[i], PY[i], c, QX[i], QY[i], c, QX[j], QY[j], c, PX[j], PY[j], c);
            quad(vc, m, PX[i], PY[i], c, PX[j], PY[j], c,
                    PX[j] + NX[j] * aa, PY[j] + NY[j] * aa, transparent, PX[i] + NX[i] * aa, PY[i] + NY[i] * aa, transparent);
        }
    }

    private static void emitShadow(VertexConsumer vc, Matrix3x2fc m, float x, float y, float w, float h,
                                   float r, float size, int c) {
        prepareRadii(w, h, r, r, r, r, pixel());
        int n = build(x, y, w, h, RADII, SEGS, PX, PY, NX, NY);
        float cx = x + w / 2f, cy = y + h / 2f;
        int transparent = c & 0x00FFFFFF;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(vc, m, cx, cy, c, PX[i], PY[i], c, PX[j], PY[j], c);
            quad(vc, m, PX[i], PY[i], c, PX[j], PY[j], c,
                    PX[j] + NX[j] * size, PY[j] + NY[j] * size, transparent,
                    PX[i] + NX[i] * size, PY[i] + NY[i] * size, transparent);
        }
    }

    // ------------------------------------------------------------------ geometry helpers

    private static int segmentsFor(float r, float px) {
        if (r <= 0.01f) return 0;
        float devicePx = r / Math.max(1e-3f, px);
        int s = (int) Math.ceil(devicePx * 0.6f);
        return Math.max(2, Math.min(MAX_SEGMENTS, s));
    }

    /**
     * Builds the outline of a rounded rectangle (corner order TL, BL, BR, TR).
     * @return number of points
     */
    private static int build(float x, float y, float w, float h, float[] radii, int[] segs,
                             float[] px, float[] py, float[] nx, float[] ny) {
        int n = 0;
        for (int corner = 0; corner < 4; corner++) {
            float r = radii[corner];
            float cx, cy;
            switch (corner) {
                case 0 -> { cx = x + r; cy = y + r; }
                case 1 -> { cx = x + r; cy = y + h - r; }
                case 2 -> { cx = x + w - r; cy = y + h - r; }
                default -> { cx = x + w - r; cy = y + r; }
            }
            double start = Math.toRadians(270 - 90 * corner);
            int s = segs[corner];
            if (s == 0) {
                double mid = start - Math.PI / 4;
                px[n] = cx;
                py[n] = cy;
                if (nx != null) {
                    nx[n] = (float) Math.cos(mid) * 1.41421f;
                    ny[n] = (float) Math.sin(mid) * 1.41421f;
                }
                n++;
                continue;
            }
            for (int i = 0; i <= s; i++) {
                double a = start - (Math.PI / 2) * i / s;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                px[n] = cx + ca * r;
                py[n] = cy + sa * r;
                if (nx != null) {
                    nx[n] = ca;
                    ny[n] = sa;
                }
                n++;
            }
        }
        return n;
    }

    /** @param px size of one device pixel in GUI units (controls the corner tessellation) */
    private static void prepareRadii(float w, float h, float tl, float tr, float br, float bl, float px) {
        float max = Math.min(w, h) / 2f;
        RADII[0] = Math.max(0, Math.min(tl, max));
        RADII[1] = Math.max(0, Math.min(bl, max));
        RADII[2] = Math.max(0, Math.min(br, max));
        RADII[3] = Math.max(0, Math.min(tr, max));
        float p = px > 0 ? px : pixel();
        for (int i = 0; i < 4; i++) SEGS[i] = segmentsFor(RADII[i], p);
    }

    private static int colorAt(float px, float py, float x, float y, float w, float h, int c1, int c2, boolean horizontal) {
        if (c1 == c2) return c1;
        float t = horizontal ? (px - x) / w : (py - y) / h;
        return ColorUtil.lerp(c1, c2, t);
    }

    // ------------------------------------------------------------------ public API

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0) return;
        int c = apply(color);
        if (ColorUtil.alpha(c) == 0) return;
        submitQuad(ctx, x, y, c, x, y + h, c, x + w, y + h, c, x + w, y, c);
    }

    public static void gradientV(DrawContext ctx, float x, float y, float w, float h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int a = apply(top), b = apply(bottom);
        if (ColorUtil.alpha(a) == 0 && ColorUtil.alpha(b) == 0) return;
        submitQuad(ctx, x, y, a, x, y + h, b, x + w, y + h, b, x + w, y, a);
    }

    public static void gradientH(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        int a = apply(left), b = apply(right);
        if (ColorUtil.alpha(a) == 0 && ColorUtil.alpha(b) == 0) return;
        submitQuad(ctx, x, y, a, x, y + h, a, x + w, y + h, b, x + w, y, b);
    }

    /** Line of arbitrary direction with the given thickness. */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        int c = apply(color);
        if (ColorUtil.alpha(c) == 0) return;
        float nx = -dy / len * thickness / 2f, ny = dx / len * thickness / 2f;
        submitQuad(ctx, x1 + nx, y1 + ny, c, x1 - nx, y1 - ny, c, x2 - nx, y2 - ny, c, x2 + nx, y2 + ny, c);
    }

    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        roundedRect(ctx, x, y, w, h, r, r, r, r, color, color, false, true);
    }

    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h,
                                   float tl, float tr, float br, float bl, int color) {
        roundedRect(ctx, x, y, w, h, tl, tr, br, bl, color, color, false, true);
    }

    public static void roundedGradient(DrawContext ctx, float x, float y, float w, float h, float r,
                                       int c1, int c2, boolean horizontal) {
        roundedRect(ctx, x, y, w, h, r, r, r, r, c1, c2, horizontal, true);
    }

    /** Core rounded rectangle fill with optional linear gradient and feathered edges. */
    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h,
                                   float tl, float tr, float br, float bl,
                                   int color1, int color2, boolean horizontal, boolean antialias) {
        if (w <= 0 || h <= 0) return;
        int c1 = apply(color1), c2 = apply(color2);
        if (ColorUtil.alpha(c1) == 0 && ColorUtil.alpha(c2) == 0) return;
        float aa = antialias ? pixel() : 0f;
        submit(ctx, new Shape(ctx, ROUNDED, x, y, w, h, tl, tr, br, bl, aa, c1, c2, 0, 0, horizontal,
                x - aa, y - aa, x + w + aa, y + h + aa));
    }

    /** Outline (ring) of a rounded rectangle; the stroke lies inside the bounds. */
    public static void roundedOutline(DrawContext ctx, float x, float y, float w, float h, float r, float thickness, int color) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        int c = apply(color);
        if (ColorUtil.alpha(c) == 0) return;
        float aa = pixel();
        submit(ctx, new Shape(ctx, OUTLINE, x, y, w, h, r, r, r, r, thickness, c, c, 0, 0, false,
                x - aa, y - aa, x + w + aa, y + h + aa));
    }

    /** Soft drop shadow: a solid core that fades to transparent over {@code size} units. */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float r, float size, int color) {
        if (w <= 0 || h <= 0) return;
        int c = apply(color);
        if (ColorUtil.alpha(c) == 0) return;
        float pad = size * 1.42f;
        submit(ctx, new Shape(ctx, SHADOW, x, y, w, h, r, r, r, r, size, c, c, 0, 0, false,
                x - pad, y - pad, x + w + pad, y + h + pad));
    }

    public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
        roundedRect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    public static void ring(DrawContext ctx, float cx, float cy, float radius, float thickness, int color) {
        roundedOutline(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, thickness, color);
    }
}
