package dev.nexus.client.render;

import net.minecraft.client.gui.DrawContext;

/** Scissor helper accepting float bounds (DrawContext keeps the actual stack and applies the matrix). */
public final class Scissor {
    private Scissor() {}

    public static void push(DrawContext ctx, float x, float y, float w, float h) {
        int x1 = (int) Math.floor(x), y1 = (int) Math.floor(y);
        int x2 = (int) Math.ceil(x + Math.max(0, w)), y2 = (int) Math.ceil(y + Math.max(0, h));
        ctx.enableScissor(x1, y1, Math.max(x1, x2), Math.max(y1, y2));
    }

    public static void pop(DrawContext ctx) {
        ctx.disableScissor();
    }
}
