package dev.nexus.client.hud;

import dev.nexus.client.Nexus;
import dev.nexus.client.NexusClient;
import dev.nexus.client.event.events.HudRenderEvent;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.ui.HudEditorScreen;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.animation.AnimationClock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix3x2fStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Renders enabled HUD modules (registered as a Fabric HUD layer) and toast notifications. */
public final class HudManager {
    private final Set<String> failed = new HashSet<>();

    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        AnimationClock.beginFrame();
        Render2D.resetAlpha();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        boolean editorOpen = mc.currentScreen instanceof HudEditorScreen;
        boolean debugHidden = Nexus.settings().hudHideInDebug.isOn() && mc.getDebugHud().shouldShowDebugHud();

        if (!editorOpen && !debugHidden) {
            List<HudModule> modules = Nexus.modules().hudModules();
            for (int i = 0; i < modules.size(); i++) {
                HudModule m = modules.get(i);
                if (m.isEnabled()) renderElement(ctx, m, false, sw, sh);
            }
        }
        Nexus.events().post(HudRenderEvent.INSTANCE.set(ctx, tickCounter.getTickProgress(false)));
        if (!editorOpen && !(mc.currentScreen instanceof NexusScreen)) {
            Nexus.notifications().render(ctx, sw, sh);
        }
    }

    /** Draws one element at its configured position, scale and opacity. */
    public boolean renderElement(DrawContext ctx, HudModule m, boolean editor, float screenWidth, float screenHeight) {
        float x = m.getX(screenWidth), y = m.getY(screenHeight), s = m.getScale();
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(s, s);
        Render2D.pushAlpha(m.getOpacity());
        try {
            return m.render(ctx, editor);
        } catch (Throwable t) {
            if (failed.add(m.getId())) NexusClient.LOGGER.error("HUD element {} failed to render", m.getId(), t);
            return false;
        } finally {
            Render2D.popAlpha();
            matrices.popMatrix();
        }
    }
}
