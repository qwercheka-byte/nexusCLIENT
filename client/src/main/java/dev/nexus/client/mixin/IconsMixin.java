package dev.nexus.client.mixin;

import dev.nexus.client.render.WindowIcon;
import net.minecraft.client.util.Icons;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

/** Vanilla's {@code Window.setIcon} gets the NEXUS icons instead of the Minecraft ones (Windows and Linux/X11). */
@Mixin(Icons.class)
public abstract class IconsMixin {
    @Inject(method = "getIcons", at = @At("HEAD"), cancellable = true)
    private void nexus$icons(ResourcePack pack, CallbackInfoReturnable<List<InputSupplier<InputStream>>> cir) {
        cir.setReturnValue(WindowIcon.icons());
    }
}
