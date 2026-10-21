package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.util.HaloVisibility;
import dev.nexus.client.module.impl.hud.PotionHudModule;
import dev.nexus.client.module.impl.visuals.CustomCrosshairModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow
    protected abstract boolean shouldRenderSpectatorCrosshair(HitResult hitResult);

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nexus$renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Nexus.modules() == null) return;
        CustomCrosshairModule crosshair = Nexus.modules().get(CustomCrosshairModule.class);
        if (crosshair == null || !crosshair.isEnabled() || client.player == null) return;
        // keep vanilla behaviour for the F3 axis indicator
        boolean debugAxes = client.getDebugHud().shouldShowDebugHud() && !client.player.hasReducedDebugInfo()
                && !client.options.getReducedDebugInfo().getValue();
        if (debugAxes) return;
        ci.cancel();
        if (!client.options.getPerspective().isFirstPerson()) return;
        if (client.interactionManager != null && client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR
                && !shouldRenderSpectatorCrosshair(client.crosshairTarget)) return;
        crosshair.render(context, tickCounter);
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void nexus$renderStatusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Nexus.modules() == null) return;
        PotionHudModule potions = Nexus.modules().get(PotionHudModule.class);
        if (potions != null && potions.isEnabled() && potions.hidesVanilla()) ci.cancel();
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void nexus$haloHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HaloVisibility.hidesVanilla()) ci.cancel();
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void nexus$haloStatusBars(DrawContext context, CallbackInfo ci) {
        if (HaloVisibility.hidesVanilla()) ci.cancel();
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void nexus$haloHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (HaloVisibility.hidesVanilla()) ci.cancel();
    }
}
