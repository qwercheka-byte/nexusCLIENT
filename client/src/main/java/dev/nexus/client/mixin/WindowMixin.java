package dev.nexus.client.mixin;

import dev.nexus.client.render.WindowIcon;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the game window the NEXUS icon before it appears on the taskbar (see {@link WindowIcon}). */
@Mixin(Window.class)
public abstract class WindowMixin {
    @Shadow
    @Final
    private long handle;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", shift = At.Shift.AFTER, remap = false))
    private void nexus$createHidden(CallbackInfo ci) {
        if (WindowIcon.showAfterIcon()) GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    }

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/util/Window;handle:J", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void nexus$iconThenShow(CallbackInfo ci) {
        if (handle == 0L) return; // creation failed; vanilla reports it
        WindowIcon.apply(handle);
        if (WindowIcon.showAfterIcon() && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE) {
            GLFW.glfwShowWindow(handle);
        }
    }
}
