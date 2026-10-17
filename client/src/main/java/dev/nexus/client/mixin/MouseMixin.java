package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.event.events.MouseButtonEvent;
import dev.nexus.client.event.events.MouseScrollEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void nexus$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (window != client.getWindow().getHandle() || Nexus.events() == null) return;
        Nexus.events().post(new MouseButtonEvent(input.button(), action));
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void nexus$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window != client.getWindow().getHandle() || client.currentScreen != null || Nexus.events() == null) return;
        if (Nexus.events().post(MouseScrollEvent.INSTANCE.set(vertical)).isCancelled()) ci.cancel();
    }
}
