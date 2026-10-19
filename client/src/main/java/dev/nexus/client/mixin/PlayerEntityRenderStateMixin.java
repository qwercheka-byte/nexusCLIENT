package dev.nexus.client.mixin;

import dev.nexus.client.render.NametagData;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerEntityRenderStateMixin implements NametagData {
    @Unique private float nexus$health = -1f;
    @Unique private float nexus$maxHealth = 20f;

    @Override
    public float nexus$getHealth() {
        return nexus$health;
    }

    @Override
    public float nexus$getMaxHealth() {
        return nexus$maxHealth;
    }

    @Override
    public void nexus$setHealth(float health, float maxHealth) {
        this.nexus$health = health;
        this.nexus$maxHealth = maxHealth;
    }
}
