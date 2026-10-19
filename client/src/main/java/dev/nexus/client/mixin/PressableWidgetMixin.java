package dev.nexus.client.mixin;

import dev.nexus.client.ui.vanilla.NexusWidgets;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
    @Shadow
    @Nullable
    private Supplier<Boolean> focusOverride;

    @Inject(method = "drawButton(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void nexus$drawButton(DrawContext ctx, CallbackInfo ci) {
        ClickableWidget w = (ClickableWidget) (Object) this;
        boolean focused = this.focusOverride != null ? this.focusOverride.get() : w.isFocused();
        NexusWidgets.button(ctx, w, w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.active, w.isHovered(), focused, w.getAlpha());
        ci.cancel();
    }
}
