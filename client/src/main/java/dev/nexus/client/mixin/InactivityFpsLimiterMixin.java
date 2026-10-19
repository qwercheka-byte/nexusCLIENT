package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.module.impl.performance.UnfocusedFpsModule;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public abstract class InactivityFpsLimiterMixin {
    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void nexus$limit(CallbackInfoReturnable<Integer> cir) {
        if (Nexus.modules() == null) return;
        UnfocusedFpsModule module = Nexus.modules().get(UnfocusedFpsModule.class);
        if (module == null || !module.isEnabled()) return;
        int limit = module.currentLimit();
        if (limit > 0 && limit < cir.getReturnValueI()) cir.setReturnValue(limit);
    }
}
