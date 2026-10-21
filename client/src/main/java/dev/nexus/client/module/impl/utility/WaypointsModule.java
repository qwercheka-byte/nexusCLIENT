package dev.nexus.client.module.impl.utility;

import dev.nexus.client.Nexus;
import dev.nexus.client.event.events.HudRenderEvent;
import dev.nexus.client.event.events.KeyEvent;
import dev.nexus.client.event.events.TickEvent;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.KeybindSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.waypoint.Waypoint;
import dev.nexus.client.waypoint.WaypointStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Player-placed world markers ("waypoints"), stored locally only:
 * <ul>
 *     <li>Press the create key (B) to drop a waypoint where you stand;</li>
 *     <li>death marks are dropped automatically at the death position;</li>
 *     <li>a compass strip on top of the screen shows direction and distance to each waypoint
 *     of the current dimension;</li>
 *     <li>management from chat: /wp list, /wp del &lt;name&gt;, /wp rename &lt;old&gt; &lt;new&gt;, /wp clear.</li>
 * </ul>
 * Everything is client-side: no packets beyond vanilla, nothing is sent to the server.
 */
public final class WaypointsModule extends Module {
    private static final int[] PALETTE = {
            0x63C7F2, 0xF27863, 0x8DF263, 0xF2D963, 0xE563F2, 0x63F2C0, 0xF2A063, 0x9E9EF2
    };
    private static final String TAG = "[Waypoint] ";

    private final KeybindSetting createKey = add(new KeybindSetting(
            "create-key", "Create waypoint", "Место метки куда смотреть: клавиша", GLFW.GLFW_KEY_B));
    private final BooleanSetting compass = add(new BooleanSetting(
            "compass", "Compass strip", "Полоса-компас с метками вверху экрана", true));
    private final BooleanSetting deathMark = add(new BooleanSetting(
            "death-marks", "Death marks", "Автоматически ставить метку на месте смерти", true));
    private final NumberSetting fovAngle = add(new NumberSetting(
            "fov", "Compass fov", "Обзор полосы-компаса в градусах", 120, 60, 170, 5).suffix("°"));

    private boolean deathMarked;
    private int serial;

    public WaypointsModule() {
        super("waypoints", "Waypoints", "Marks on the world: create with B, death marks, compass strip with distances.",
                Category.UTILITY, true);
        on(KeyEvent.class, e -> {
            if (e.isPress() && createKey.matchesKey(e.key()) && mc.currentScreen == null) createAtPlayer("Метка");
        });
        on(TickEvent.class, e -> tick());
        on(HudRenderEvent.class, this::renderHud);
    }

    // ------------------------------------------------------------------------------------------------ actions

    private void createAtPlayer(String base) {
        if (mc.player == null || mc.world == null) return;
        WaypointStore store = WaypointStore.INSTANCE;
        serial++;
        String name = base + " " + (store.all().size() + 1);
        Waypoint wp = new Waypoint(name, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                dimensionId(), PALETTE[(store.all().size()) % PALETTE.length]);
        store.add(wp);
        chat(TAG + "Поставлена метка: " + name + "  §7(" + (long) wp.x + " " + (long) wp.y + " " + (long) wp.z + ")");
    }

    private String dimensionId() {
        return mc.world.getRegistryKey().getValue().toString();
    }

    private void tick() {
        if (mc.player == null || mc.world == null) {
            deathMarked = false;
            return;
        }
        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (dead && !deathMarked && deathMark.get()) {
            deathMarked = true;
            Waypoint wp = new Waypoint("☠ Смерть", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    dimensionId(), 0xEF5A5A);
            WaypointStore.INSTANCE.add(wp);
            chat(TAG + "Записано место смерти:  §7(" + (long) wp.x + " " + (long) wp.y + " " + (long) wp.z + ")");
        } else if (!dead) {
            deathMarked = false;
        }
    }

    // ------------------------------------------------------------------------------------------------ HUD (compass strip)

    private void renderHud(HudRenderEvent e) {
        if (!compass.get() || mc.player == null || mc.getDebugHud().shouldShowDebugHud()) return;
        DrawContext ctx = e.context;
        List<Waypoint> list = WaypointStore.INSTANCE.inDimension(dimensionId());
        if (list.isEmpty()) return;

        int sw = ctx.getScaledWindowWidth();
        float width = Math.min(320.0F, sw * 0.52F);
        float cx = sw / 2.0F;
        float x0 = cx - width / 2.0F;
        float y0 = 6.0F;
        float h = 10.0F;
        float cxFov = fovAngle.getFloat();
        float yawDeg = wrap180(mc.player.getYaw((float) e.tickDelta)); // -180..180

        Render2D.roundedRect(ctx, x0, y0, width, h, 4.0F, 0x55000000);
        // cardinal letters (MC yaw: 0 = South, 90 = West, ±180 = North, -90 = East)
        label(ctx, yawDeg, 0,    cx, width, y0, "S", -1);
        label(ctx, yawDeg, 90,   cx, width, y0, "W", -1);
        label(ctx, yawDeg, -90,  cx, width, y0, "E", -1);
        label(ctx, yawDeg, 180,  cx, width, y0, "N", -1);

        // waypoints
        float py = y0 + h + 1.5F;
        for (Waypoint wp : list) {
            double ang = Math.toDegrees(Math.atan2(-(wp.x - mc.player.getX()), wp.z - mc.player.getZ()));
            float rel = wrap180((float) ang - yawDeg);
            if (Math.abs(rel) > cxFov / 2.0F) continue;
            float mx = cx + rel / cxFov * width;
            int c = 0xFF000000 | wp.color;
            Render2D.rect(ctx, mx - 1.5F, y0 + 5.0F, 3, 3, c);
            String nm = wp.name;
            int nw = TextRender.widthRaw(nm);
            TextRender.draw(ctx, Text.literal(nm), mx - nw / 2.0F, py, c, false);
            double dist = wp.distanceTo(mc.player.getX(), mc.player.getZ());
            String d = dist < 1000 ? Math.round(dist) + " м" : String.format(Locale.ROOT, "%.1f км", dist / 1000.0F);
            int dw = TextRender.widthRaw(d);
            TextRender.draw(ctx, Text.literal(d), mx - dw / 2.0F, py + 9.5F, 0xB2FFFFFF, false);
        }
    }

    private void label(DrawContext ctx, float yaw, float deg, float cx, float width, float y0, String text, int color) {
        float rel = wrap180(deg - yaw);
        float fov = fovAngle.getFloat();
        if (Math.abs(rel) > fov / 2.0F) return;
        float mx = cx + rel / fov * width;
        int tw = TextRender.widthRaw(text);
        TextRender.draw(ctx, Text.literal(text), mx - tw / 2.0F, y0 + 1.5F, color, false);
    }

    private static float wrap180(float a) {
        a = a % 360.0F;
        if (a > 180.0F) a -= 360.0F;
        if (a < -180.0F) a += 360.0F;
        return a;
    }

    private void chat(String msg) {
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§6" + msg));
        }
    }

    // ------------------------------------------------------------------------------------------------ chat command (/wp …)

    /** Called by the message-send hook; returns true when the text was a /wp command (not sent to server). */
    public static boolean handleCommand(String message) {
        if (!message.startsWith("/wp ") && !message.equals("/wp")) return false;
        WaypointsModule mod = Nexus.modules() != null ? Nexus.modules().get(WaypointsModule.class) : null;
        if (mod == null) return true; // silently consumed even with the module off
        WaypointStore store = WaypointStore.INSTANCE;
        String[] parts = message.split("\\s+");
        String dim = mod.mc.world != null ? mod.dimensionId() : "minecraft:overworld";
        switch (parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "") {
            case "list" -> {
                List<Waypoint> in = store.inDimension(dim);
                mod.chat(TAG + (in.isEmpty() ? "Метки этой стороны пусты." : "Меток: " + in.size() + " — " +
                        String.join(", ", in.stream().map(w -> w.name).limit(8).toList()) + (in.size() > 8 ? "…" : "")));
            }
            case "del" -> {
                if (parts.length < 3) mod.chat(TAG + "Формат: /wp del <имя>");
                else if (!store.removeByName(parts[2])) mod.chat(TAG + "Метка не найдена: " + parts[2]);
                else mod.chat(TAG + "Удалена метка: " + parts[2]);
            }
            case "rename" -> {
                if (parts.length < 4) mod.chat(TAG + "Формат: /wp rename <старое> <новое>");
                else if (!store.rename(parts[2], parts[3])) mod.chat(TAG + "Метка не найдена: " + parts[2]);
                else mod.chat(TAG + "Метка переименована → " + parts[3]);
            }
            case "clear" -> {
                int removed = store.clear(dim);
                mod.chat(TAG + "Удалено меток этой стороны: " + removed);
            }
            default -> mod.chat(TAG + "§7Клавиша B — метка. Команды: /wp list · /wp del <имя> · /wp rename <ст> <нов> · /wp clear");
        }
        return true;
    }
}
