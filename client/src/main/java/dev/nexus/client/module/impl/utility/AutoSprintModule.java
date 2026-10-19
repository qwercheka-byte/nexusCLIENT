package dev.nexus.client.module.impl.utility;

import dev.nexus.client.event.events.TickEvent;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;

/** Starts sprinting whenever vanilla would allow it while you move forward (same as holding sprint). */
public final class AutoSprintModule extends Module {
    public AutoSprintModule() {
        super("auto_sprint", "Auto Sprint", "Sprints automatically while moving forward, following vanilla sprint rules.",
                Category.UTILITY);
        on(TickEvent.class, e -> tick());
    }

    private void tick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || p.isSprinting()) return;
        boolean canSprint = p.input.hasForwardMovement()
                && !p.isSneaking()
                && !p.isUsingItem()
                && !p.horizontalCollision
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying);
        if (canSprint) p.setSprinting(true);
    }
}
