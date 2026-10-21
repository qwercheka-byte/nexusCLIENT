package dev.nexus.client.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldownManager.class)
public interface ItemCooldownAccessor {
    @Accessor("tick")
    int nexus$tick();

    @Accessor("entries")
    Map<Identifier, ?> nexus$entries();
}
