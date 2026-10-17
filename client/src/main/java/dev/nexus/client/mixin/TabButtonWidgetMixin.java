package dev.nexus.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabButtonWidget.class)
public abstract class TabButtonWidgetMixin {
    @Redirect(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
        )
    )
    private void nexus$tab(DrawContext ctx, RenderPipeline p, Identifier id, int x, int y, int w, int h) {
        TabButtonWidget self = (TabButtonWidget) (Object) this;
        NexusWidgets.tab(ctx, self, x, y, w, h, self.isCurrentTab(), self.isHovered() || self.isFocused());
    }

    @Inject(method = "renderBackgroundTexture(Lnet/minecraft/client/gui/DrawContext;IIII)V", at = @At("HEAD"), cancellable = true)
    private void nexus$noTabBackground(DrawContext ctx, int x1, int y1, int x2, int y2, CallbackInfo ci) {
        ci.cancel();
    }

    @Redirect(
        method = "drawCurrentTabLine(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
        )
    )
    private void nexus$underline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        NexusWidgets.tabUnderline(ctx, x1, y1, x2 - x1);
    }
}
