package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.module.impl.visuals.FullbrightModule;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes sure options.txt always receives the user's real gamma, never the Fullbright override. */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
    @Inject(method = "write", at = @At("HEAD"))
    private void nexus$beforeWrite(CallbackInfo ci) {
        FullbrightModule fb = fullbright();
        if (fb != null) fb.beforeOptionsWrite();
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void nexus$afterWrite(CallbackInfo ci) {
        FullbrightModule fb = fullbright();
        if (fb != null) fb.afterOptionsWrite();
    }

    private static FullbrightModule fullbright() {
        return Nexus.modules() == null ? null : Nexus.modules().get(FullbrightModule.class);
    }
}
