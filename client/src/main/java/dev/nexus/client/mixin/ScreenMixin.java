package dev.nexus.client.mixin;

import dev.nexus.client.ui.vanilla.MenuBackground;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;IIII)V", at = @At("HEAD"), cancellable = true)
    private void nexus$darkening(DrawContext ctx, int x, int y, int w, int h, CallbackInfo ci) {
        NexusWidgets.darkening(ctx, x, y, w, h, MinecraftClient.getInstance().world != null);
        ci.cancel();
    }

    @Inject(method = "renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("HEAD"), cancellable = true)
    private void nexus$photos(DrawContext ctx, float delta, CallbackInfo ci) {
        if (MenuBackground.get().render(ctx, this.width, this.height)) ci.cancel();
    }

    @Inject(method = "renderInGameBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void nexus$inGame(DrawContext ctx, CallbackInfo ci) {
        NexusWidgets.inGameBackground(ctx, this.width, this.height);
        ci.cancel();
    }
}
