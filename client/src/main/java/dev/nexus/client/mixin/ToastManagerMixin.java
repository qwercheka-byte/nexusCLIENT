package dev.nexus.client.mixin;

import dev.nexus.client.util.HaloVisibility;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.toast.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the vanilla movement/jump tutorial toasts ("Move with W, A, S and D") when the Halo HUD is active. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void nexus$hideTutorialToasts(Toast toast, CallbackInfo ci) {
        if (toast instanceof TutorialToast && HaloVisibility.hidesVanilla()) ci.cancel();
    }
}
