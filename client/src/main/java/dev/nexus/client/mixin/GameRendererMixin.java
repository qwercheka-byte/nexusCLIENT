package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.module.impl.utility.ZoomModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nexus$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        // only the world projection (changingFov = true) is zoomed, not the first-person hand
        if (!changingFov || Nexus.modules() == null) return;
        ZoomModule zoom = Nexus.modules().get(ZoomModule.class);
        if (zoom == null) return;
        float multiplier = zoom.fovMultiplier();
        if (multiplier != 1f) cir.setReturnValue(cir.getReturnValueF() * multiplier);
    }
}
