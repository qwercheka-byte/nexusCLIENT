package dev.nexus.client.module.impl.visuals;

import dev.nexus.client.render.NametagData;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ColorSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.NumberSetting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Restyles player nametags (scale, background, health). Visibility rules are untouched:
 * nametags only render where vanilla renders them, and sneaking players stay hidden behind walls.
 */
public final class CustomNametagsModule extends Module {
    private final NumberSetting scale = add(new NumberSetting("scale", "Scale", "Size of the nametag",
            1.0, 0.5, 2.0, 0.05).displayMultiplier(100).suffix("%"));
    private final BooleanSetting background = add(new BooleanSetting("background", "Background",
            "Draw a background behind the name", true));
    private final ColorSetting backgroundColor = add(new ColorSetting("background_color", "Background color",
            "Colour and transparency of the background", 0x66000000, true)).visibleWhen(background::isOn);
    private final BooleanSetting health = add(new BooleanSetting("health", "Show health",
            "Append the player's health", true));
    private final ModeSetting healthFormat = add(new ModeSetting("health_format", "Health format",
            "How health is displayed", "Hearts", "Hearts", "Points", "Percent")).visibleWhen(health::isOn);
    private final BooleanSetting shadow = add(new BooleanSetting("shadow", "Text shadow",
            "Draw text with a shadow", false));

    public CustomNametagsModule() {
        super("custom_nametags", "Custom Nametags", "Restyles player nametags with scale, background and health.",
                Category.VISUALS);
    }

    /** Height of one label line in label space (vanilla: 9 * 1.15 world units / 0.025). */
    private static final float LINE_HEIGHT = 9f * 1.15f;

    /**
     * Replacement for PlayerEntityRenderer#renderLabelIfPresent; mirrors the vanilla label transform and
     * see-through rules (LabelCommandRenderer), adding scale, a custom background and health.
     */
    public void renderLabel(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
                            CameraRenderState camera) {
        Vec3d pos = state.nameLabelPos;
        if (pos == null || (state.playerName == null && state.displayName == null)) return;
        float s = scale.getFloat();
        float y = state.extraEars ? -10 : 0;

        matrices.push();
        // keep the bottom edge where vanilla puts it when the tag is scaled up
        matrices.translate(pos.x, pos.y + 0.5 + 0.225 * (s - 1f), pos.z);
        matrices.multiply(camera.orientation);
        matrices.scale(0.025f * s, -0.025f * s, 0.025f * s);
        if (state.playerName != null) {
            // "below name" scoreboard line (vanilla stores it in playerName)
            submitLine(matrices, queue, state.playerName, y, state);
            y -= LINE_HEIGHT;
        }
        if (state.displayName != null) {
            Text label = state.displayName;
            if (health.isOn() && state instanceof NametagData data && data.nexus$getHealth() >= 0) {
                label = withHealth(label, data.nexus$getHealth(), data.nexus$getMaxHealth());
            }
            submitLine(matrices, queue, label, y, state);
        }
        matrices.pop();
    }

    private void submitLine(MatrixStack matrices, OrderedRenderCommandQueue queue, Text label, float y,
                            PlayerEntityRenderState state) {
        OrderedText text = label.asOrderedText();
        float x = -mc.textRenderer.getWidth(label) / 2f;
        int bg = background.isOn() ? backgroundColor.get() : 0;
        if (!state.sneaking) {
            // dim copy visible through walls (like vanilla) + full-bright copy in front
            queue.submitText(matrices, x, y, text, false, TextRenderer.TextLayerType.SEE_THROUGH, state.light, 0x80FFFFFF, bg, 0);
            queue.submitText(matrices, x, y, text, shadow.isOn(), TextRenderer.TextLayerType.NORMAL,
                    LightmapTextureManager.applyEmission(state.light, 2), 0xFFFFFFFF, 0, 0);
        } else {
            queue.submitText(matrices, x, y, text, false, TextRenderer.TextLayerType.NORMAL, state.light, 0x80FFFFFF, bg, 0);
        }
    }

    private Text withHealth(Text name, float hp, float max) {
        float ratio = max > 0 ? hp / max : 0;
        int color = ratio > 0.6f ? 0x55FF55 : ratio > 0.3f ? 0xFFDD55 : 0xFF5555;
        String value = switch (healthFormat.get()) {
            case "Points" -> String.format(Locale.ROOT, "%.0f", hp);
            case "Percent" -> Math.round(ratio * 100) + "%";
            default -> String.format(Locale.ROOT, "%.1f\u2764", hp / 2f);
        };
        MutableText out = Text.empty().append(name).append(Text.literal(" " + value).styled(st -> st.withColor(color)));
        return out;
    }
}
