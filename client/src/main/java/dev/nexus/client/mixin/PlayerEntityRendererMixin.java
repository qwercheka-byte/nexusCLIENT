package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.module.impl.visuals.CustomNametagsModule;
import dev.nexus.client.render.NametagData;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player nametags. Copies health into the render state, and lets Custom Nametags submit the label in its own
 * style. Which labels exist (and their see-through rules) is still decided by vanilla when it fills the state.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void nexus$captureHealth(PlayerLikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        ((NametagData) state).nexus$setHealth(player.getHealth() + player.getAbsorptionAmount(), player.getMaxHealth());
    }

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void nexus$renderLabel(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                   CameraRenderState camera, CallbackInfo ci) {
        if (Nexus.modules() == null) return;
        CustomNametagsModule nametags = Nexus.modules().get(CustomNametagsModule.class);
        if (nametags == null || !nametags.isEnabled()) return;
        ci.cancel();
        nametags.renderLabel(state, matrices, queue, camera);
    }
}
