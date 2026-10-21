package dev.nexus.client.mixin;

import dev.nexus.client.util.HaloVisibility;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.gui.hud.bar.JumpBar;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** XP bar + level number and the mount jump bar are hidden when the Halo HUD replaces the vanilla HUD. */
@Mixin({ExperienceBar.class, JumpBar.class})
public abstract class HudBarHidingMixin {
    @Inject(method = "renderBar", at = @At("HEAD"), cancellable = true)
    private void nexus$haloBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HaloVisibility.hidesVanilla()) ci.cancel();
    }

    @Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true)
    private void nexus$haloBarAddons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HaloVisibility.hidesVanilla()) ci.cancel();
    }
}
