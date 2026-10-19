package dev.nexus.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CheckboxWidget.class)
public abstract class CheckboxWidgetMixin {
    @Shadow
    private boolean checked;

    @Redirect(
        method = "drawIcon(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V"
        )
    )
    private void nexus$box(DrawContext ctx, RenderPipeline pipeline, Identifier id, int x, int y, int w, int h, int color) {
        ClickableWidget self = (ClickableWidget) (Object) this;
        NexusWidgets.checkbox(ctx, self, x, y, w, this.checked, self.isFocused() || self.isHovered(), self.getAlpha());
    }
}
