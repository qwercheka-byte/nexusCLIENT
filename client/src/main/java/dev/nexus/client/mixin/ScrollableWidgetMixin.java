package dev.nexus.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ScrollableWidget.class)
public abstract class ScrollableWidgetMixin {
    private static final String DRAW = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V";

    @Redirect(
        method = "drawScrollbar(Lnet/minecraft/client/gui/DrawContext;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
            ordinal = 0
        )
    )
    private void nexus$track(DrawContext ctx, RenderPipeline p, Identifier id, int x, int y, int w, int h) {
        NexusWidgets.scrollbar(ctx, x, y, w, h, false);
    }

    @Redirect(
        method = "drawScrollbar(Lnet/minecraft/client/gui/DrawContext;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
            ordinal = 1
        )
    )
    private void nexus$thumb(DrawContext ctx, RenderPipeline p, Identifier id, int x, int y, int w, int h) {
        NexusWidgets.scrollbar(ctx, x, y, w, h, true);
    }
}
