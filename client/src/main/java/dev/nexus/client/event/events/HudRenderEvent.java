package dev.nexus.client.event.events;

import net.minecraft.client.gui.DrawContext;

/** Posted while the in-game HUD is drawn. A single mutable instance is reused to avoid allocations. */
public final class HudRenderEvent {
    public static final HudRenderEvent INSTANCE = new HudRenderEvent();

    public DrawContext context;
    public float tickDelta;

    private HudRenderEvent() {}

    public HudRenderEvent set(DrawContext context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
        return this;
    }
}
