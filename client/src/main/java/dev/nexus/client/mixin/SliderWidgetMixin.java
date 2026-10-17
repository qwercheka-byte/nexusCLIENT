package dev.nexus.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin {
    @Shadow
    protected double value;
    private static final String DRAW = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V";

    @Redirect(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V",
            ordinal = 0
        )
    )
    private void nexus$track(DrawContext ctx, RenderPipeline pipeline, Identifier id, int x, int y, int w, int h, int color) {
        ClickableWidget self = (ClickableWidget) (Object) this;
        NexusWidgets.sliderTrack(ctx, self, x, y, w, h, this.value, self.active, self.isHovered(), self.isFocused(), self.getAlpha());
    }

    @Redirect(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V",
            ordinal = 1
        )
    )
    private void nexus$handle(DrawContext ctx, RenderPipeline pipeline, Identifier id, int x, int y, int w, int h, int color) {
        ClickableWidget self = (ClickableWidget) (Object) this;
        NexusWidgets.sliderHandle(ctx, self, x, y, w, h, self.active, self.getAlpha());
    }
}
