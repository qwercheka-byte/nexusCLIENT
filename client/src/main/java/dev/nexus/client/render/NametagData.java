package dev.nexus.client.render;

/** Extra data attached to player render states (implemented by {@link PlayerEntityRenderStateMixin}). */
public interface NametagData {
    float nexus$getHealth();

    float nexus$getMaxHealth();

    void nexus$setHealth(float health, float maxHealth);
}
