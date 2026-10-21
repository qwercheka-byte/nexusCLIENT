package dev.nexus.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Time accessor for the private inner Entry of ItemCooldownManager. */
@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface ItemCooldownEntryAccessor {
    @Accessor("endTick")
    int nexus$end();
}
