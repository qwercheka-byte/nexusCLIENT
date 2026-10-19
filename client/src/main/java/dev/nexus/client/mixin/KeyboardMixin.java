package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.event.events.KeyEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"))
    private void nexus$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (window != client.getWindow().getHandle() || Nexus.events() == null || input.key() < 0) return;
        Nexus.events().post(new KeyEvent(input.key(), input.scancode(), action, input.modifiers()));
    }
}
