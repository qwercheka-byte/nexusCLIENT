package dev.nexus.client.mixin;

import dev.nexus.client.NexusClient;
import dev.nexus.client.render.WindowIcon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Second icon pass on Windows: vanilla calls {@code Window.setIcon} during {@code MinecraftClient}'s constructor,
 * after the window was already shown by {@link WindowMixin}. Some Windows builds only refresh the taskbar icon when
 * the window goes through a hide/show cycle, so after the constructor finishes we hide the window, set the NEXUS
 * icon again and show it back. On other platforms this runs nowhere (the window icon was set by WindowMixin already).
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientIconMixin {
    @Shadow
    public abstract Window getWindow();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nexus$iconAgainAfterInit(CallbackInfo ci) {
        if (!WindowIcon.showAfterIcon()) return;
        long handle = getWindow().getHandle();
        GLFW.glfwHideWindow(handle);
        WindowIcon.apply(handle, "init");
        GLFW.glfwShowWindow(handle);
        NexusClient.LOGGER.info("WindowIcon[init]: NEXUS icon re-applied after vanilla setIcon (hide/show cycle)");
    }
}
