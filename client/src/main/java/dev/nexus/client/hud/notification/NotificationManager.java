package dev.nexus.client.hud.notification;

import dev.nexus.client.Nexus;
import dev.nexus.client.event.events.ModuleToggleEvent;
import dev.nexus.client.module.impl.interfaces.NotificationsModule;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Queue and renderer of toast notifications. */
public final class NotificationManager {
    private static final int MAX_VISIBLE = 5;
    private static final float WIDTH = 150f, HEIGHT = 30f, SPACING = 4f, MARGIN = 6f;
    private final List<Notification> active = new ArrayList<>();

    public void init() {
        Nexus.events().subscribe(ModuleToggleEvent.class, e -> {
            NotificationsModule settings = settings();
            if (e.byKeybind() && settings != null && settings.isEnabled() && settings.showToggles()) {
                post(e.module().getName(), e.enabled() ? "Enabled" : "Disabled",
                        e.enabled() ? Notification.Type.MODULE_ON : Notification.Type.MODULE_OFF);
            }
        });
    }

    private NotificationsModule settings() {
        return Nexus.modules() == null ? null : Nexus.modules().get(NotificationsModule.class);
    }

    /** Must be called on the client thread. */
    public void post(String title, String message, Notification.Type type) {
        NotificationsModule s = settings();
        long duration = s != null ? s.durationMs() : 2500;
        // replace an existing toast with the same title (e.g. toggling a module repeatedly)
        for (Notification n : active) {
            if (!n.leaving && n.title.equals(title)) {
                n.leaving = true;
                n.visibility.animateTo(0f, 120);
            }
        }
        active.add(new Notification(title, message, type, duration));
        int visible = 0;
        for (int i = active.size() - 1; i >= 0; i--) {
            Notification n = active.get(i);
            if (n.leaving) continue;
            if (++visible > MAX_VISIBLE) {
                n.leaving = true;
                n.visibility.animateTo(0f);
            }
        }
    }

    public void render(DrawContext ctx, int screenWidth, int screenHeight) {
        if (active.isEmpty()) return;
        NotificationsModule s = settings();
        String pos = s != null ? s.position() : "Bottom Right";
        boolean right = pos.endsWith("Right");
        boolean bottom = pos.startsWith("Bottom");
        long now = System.currentTimeMillis();

        int slotIndex = 0;
        for (int i = active.size() - 1; i >= 0; i--) {
            Notification n = active.get(i);
            if (n.shownAt < 0) {
                n.shownAt = now;
                n.visibility.animateTo(1f);
            }
            if (!n.leaving && now - n.shownAt > n.durationMs) {
                n.leaving = true;
                n.visibility.animateTo(0f, 220);
            }
            float vis = n.visibility.get();
            if (n.leaving && vis <= 0.001f) {
                active.remove(i);
                continue;
            }
            float targetSlot = slotIndex;
            if (n.slot.get() < 0) n.slot.snap(targetSlot);
            float slot = n.slot.update(targetSlot);
            if (!n.leaving) slotIndex++;

            float offset = (1f - vis) * (WIDTH + MARGIN) * (right ? 1 : -1);
            float x = (right ? screenWidth - WIDTH - MARGIN : MARGIN) + offset;
            float y = bottom ? screenHeight - MARGIN - HEIGHT - slot * (HEIGHT + SPACING) : MARGIN + slot * (HEIGHT + SPACING);
            drawToast(ctx, n, x, y, vis, now);
        }
    }

    private void drawToast(DrawContext ctx, Notification n, float x, float y, float vis, long now) {
        Render2D.pushAlpha(vis);
        int color = n.type.color();
        Render2D.shadow(ctx, x, y + 1, WIDTH, HEIGHT, 6, 6, 0x50000000);
        Render2D.roundedRect(ctx, x, y, WIDTH, HEIGHT, 6, 0xF2141519);
        Render2D.roundedOutline(ctx, x, y, WIDTH, HEIGHT, 6, 1, Theme.BORDER);
        // icon bubble
        Render2D.circle(ctx, x + 15, y + HEIGHT / 2f, 8, (color & 0x00FFFFFF) | 0x30000000);
        IconRender.drawCentered(ctx, n.type.icon, x + 15, y + HEIGHT / 2f, 10, color);
        if (n.titleShown == null) {
            n.titleShown = TextRender.ellipsize(n.title, (int) WIDTH - 36);
            n.messageShown = TextRender.ellipsize(n.message, (int) WIDTH - 36);
        }
        TextRender.draw(ctx, n.titleShown, x + 28, y + 6, Theme.TEXT);
        TextRender.draw(ctx, n.messageShown, x + 28, y + 17, Theme.TEXT_MUTED);
        // progress line
        if (n.shownAt > 0) {
            float p = 1f - Math.min(1f, (now - n.shownAt) / (float) n.durationMs);
            Render2D.rect(ctx, x + 6, y + HEIGHT - 2, (WIDTH - 12) * p, 1, (color & 0x00FFFFFF) | 0x90000000);
        }
        Render2D.popAlpha();
    }

    public void clear() {
        active.clear();
    }
}
