package dev.nexus.client.hud;

import dev.nexus.client.Nexus;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;

/** Shared look of HUD elements so every element matches. */
public final class HudStyle {
    public static final float PAD_X = 5f;
    public static final float PAD_Y = 4f;
    public static final int PANEL = 0xB4101116;
    public static final int LABEL = 0xFF9A9CAD;
    public static final int VALUE = 0xFFF1F2F6;

    private HudStyle() {}

    public static float radius() {
        return Nexus.settings().hudRadius.getFloat();
    }

    public static boolean shadow() {
        return Nexus.settings().hudTextShadow.isOn();
    }

    /** Panel background + optional accent line, respecting the element's background toggle. */
    public static void panel(DrawContext ctx, HudModule module, float w, float h) {
        if (!module.hasBackground()) return;
        float r = radius();
        Render2D.roundedRect(ctx, 0, 0, w, h, r, PANEL);
        if (Nexus.settings().hudAccentLine.isOn()) {
            float inset = Math.max(r, 2f);
            Render2D.rect(ctx, inset, 0, w - inset * 2, 1f, Theme.accent());
        }
    }

    public static void text(DrawContext ctx, String s, float x, float y, int color) {
        TextRender.draw(ctx, s, x, y, color, shadow());
    }
}
