package dev.nexus.client.event.events;

import net.minecraft.entity.Entity;

/** Posted when the local player attacks an entity. */
public record AttackEntityEvent(Entity target) {}
