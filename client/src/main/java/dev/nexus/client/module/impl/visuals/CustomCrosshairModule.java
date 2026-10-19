package dev.nexus.client.module.impl.visuals;

import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ColorSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** Replaces the vanilla crosshair (rendered from InGameHudMixin, so vanilla visibility rules still apply). */
public final class CustomCrosshairModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Shape of the crosshair",
            "Cross", "Cross", "Cross + Dot", "Dot", "Circle", "Circle + Dot", "T-Shape"));
    private final NumberSetting length = add(new NumberSetting("length", "Length", "Length of each arm",
            4.5, 1, 20, 0.5).suffix("px"));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", "Width of the lines",
            1.0, 0.5, 6, 0.25).suffix("px"));
    private final NumberSetting gap = add(new NumberSetting("gap", "Gap", "Distance from the centre",
            2.0, 0, 12, 0.25).suffix("px"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", "Crosshair colour", 0xFFFFFFFF, true));
    private final BooleanSetting outline = add(new BooleanSetting("outline", "Outline", "Dark outline for contrast", true));
    private final ColorSetting outlineColor = add(new ColorSetting("outline_color", "Outline color", "Outline colour",
            0xB0000000, true)).visibleWhen(outline::isOn);
    private final NumberSetting outlineWidth = add(new NumberSetting("outline_width", "Outline width",
            "Outline thickness", 0.5, 0.25, 3, 0.25).suffix("px")).visibleWhen(outline::isOn);
    private final BooleanSetting dynamic = add(new BooleanSetting("dynamic", "Dynamic gap",
            "Spread the arms while moving or in the air", false));
    private final BooleanSetting targetHighlight = add(new BooleanSetting("target_highlight", "Target color",
            "Change colour while aiming at a living entity", false));
    private final ColorSetting targetColor = add(new ColorSetting("target_color", "Target color value",
            "Colour used while aiming at an entity", 0xFFFF5A5A, true)).visibleWhen(targetHighlight::isOn);
    private final BooleanSetting attackIndicator = add(new BooleanSetting("attack_indicator", "Attack indicator",
            "Show the attack cooldown under the crosshair", true));

    private final SmoothValue spread = new SmoothValue(0f, 12f);

    public CustomCrosshairModule() {
        super("custom_crosshair", "Custom Crosshair", "Fully customizable crosshair: shape, size, gap, colour and outline.",
                Category.VISUALS);
    }

    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        float px = 1f / (float) mc.getWindow().getScaleFactor();
        float cx = Math.round(ctx.getScaledWindowWidth() / 2f / px) * px;
        float cy = Math.round(ctx.getScaledWindowHeight() / 2f / px) * px;

        float extra = 0f;
        if (dynamic.isOn()) {
            double vx = player.getVelocity().x, vz = player.getVelocity().z;
            boolean moving = vx * vx + vz * vz > 0.0004;
            extra = spread.update((moving ? 1.5f : 0f) + (player.isOnGround() ? 0f : 2f));
        }
        int main = color.get();
        if (targetHighlight.isOn() && isAimingAtEntity()) main = targetColor.get();

        float t = thickness.getFloat(), len = length.getFloat(), g = gap.getFloat() + extra;
        if (outline.isOn()) drawShape(ctx, cx, cy, t, len, g, outlineWidth.getFloat(), outlineColor.get());
        drawShape(ctx, cx, cy, t, len, g, 0f, main);

        if (attackIndicator.isOn()) {
            float progress = player.getAttackCooldownProgress(0f);
            if (progress < 1f) {
                float w = 16f, h = 2f;
                float y = cy + g + (hasArms() ? len : t) + 4f;
                Render2D.roundedRect(ctx, cx - w / 2f - 0.5f, y - 0.5f, w + 1f, h + 1f, 1.5f, 0x90000000);
                Render2D.roundedRect(ctx, cx - w / 2f, y, w * progress, h, 1f, Theme.accent());
            }
        }
    }

    private boolean hasArms() {
        return style.is("Cross") || style.is("Cross + Dot") || style.is("T-Shape");
    }

    private boolean isAimingAtEntity() {
        HitResult hit = mc.crosshairTarget;
        return hit != null && hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult e
                && e.getEntity() instanceof LivingEntity;
    }

    /** Draws the shape; {@code grow} &gt; 0 draws the outline pass (every part expanded). */
    private void drawShape(DrawContext ctx, float cx, float cy, float t, float len, float g, float grow, int c) {
        String s = style.get();
        boolean arms = hasArms();
        boolean dot = s.equals("Dot") || s.endsWith("+ Dot");
        boolean circle = s.startsWith("Circle");
        if (arms) {
            float h = t / 2f;
            if (!s.equals("T-Shape")) bar(ctx, cx - h, cy - g - len, t, len, grow, c);
            bar(ctx, cx - h, cy + g, t, len, grow, c);
            bar(ctx, cx - g - len, cy - h, len, t, grow, c);
            bar(ctx, cx + g, cy - h, len, t, grow, c);
        }
        if (circle) {
            float radius = Math.max(t, g + len / 2f);
            Render2D.ring(ctx, cx, cy, radius + grow, t + grow * 2, c);
        }
        if (dot) {
            float size = Math.max(t, 1f) + grow * 2;
            Render2D.roundedRect(ctx, cx - size / 2f, cy - size / 2f, size, size, size / 2f, c);
        }
    }

    private static void bar(DrawContext ctx, float x, float y, float w, float h, float grow, int c) {
        Render2D.rect(ctx, x - grow, y - grow, w + grow * 2, h + grow * 2, c);
    }
}
