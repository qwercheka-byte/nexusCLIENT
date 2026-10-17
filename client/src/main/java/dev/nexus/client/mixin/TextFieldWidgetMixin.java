package dev.nexus.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {
    @Redirect(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
        )
    )
    private void nexus$background(DrawContext ctx, RenderPipeline pipeline, Identifier id, int x, int y, int w, int h) {
        ClickableWidget self = (ClickableWidget) (Object) this;
        NexusWidgets.textField(ctx, self, x, y, w, h, self.active, self.isFocused());
    }
}
