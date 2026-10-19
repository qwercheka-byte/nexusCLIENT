package dev.nexus.client.mixin;

import dev.nexus.client.ui.vanilla.NexusWidgets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.EntryListWidget.Entry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetMixin {
    @Inject(method = "drawMenuListBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void nexus$background(DrawContext ctx, CallbackInfo ci) {
        ClickableWidget w = (ClickableWidget) (Object) this;
        NexusWidgets.listBackground(ctx, w.getX(), w.getY(), w.getWidth(), w.getHeight(), MinecraftClient.getInstance().world != null);
        ci.cancel();
    }

    @Inject(method = "drawHeaderAndFooterSeparators(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void nexus$separators(DrawContext ctx, CallbackInfo ci) {
        ClickableWidget w = (ClickableWidget) (Object) this;
        NexusWidgets.listSeparators(ctx, w.getX(), w.getY(), w.getBottom(), w.getWidth());
        ci.cancel();
    }

    @Inject(
        method = "drawSelectionHighlight(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/gui/widget/EntryListWidget$Entry;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void nexus$selection(DrawContext ctx, Entry<?> entry, int color, CallbackInfo ci) {
        NexusWidgets.selection(ctx, entry.getX(), entry.getY(), entry.getWidth(), entry.getHeight(), color == -1);
        ci.cancel();
    }
}
