package dev.nexus.client.module.impl.hud;

import dev.nexus.client.Nexus;
import dev.nexus.client.event.events.HudRenderEvent;
import dev.nexus.client.event.events.ModuleToggleEvent;
import dev.nexus.client.event.events.TickEvent;
import dev.nexus.client.event.events.WorldJoinEvent;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.mixin.ItemCooldownAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Halo"-style HUD pack, one composite module (all panels are positioned like the reference shot):
 * top-left info pill (avatar, nick, fps, ping, clock) + server pill (address, TPS estimate),
 * left column: active module list parsed from the enabled modules,
 * top-right: potion effects card + item cooldowns card (reads the real ItemCooldownManager),
 * centre-above-crosshair target pill (head, name, hp bar) with small stat chips,
 * bottom-left coordinates pill with blocks-per-second, bottom-right notification toasts.
 * Everything is read from vanilla client state — nothing is sent to the server.
 */
public final class HaloHudModule extends Module {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    // ---------------------------------------------------------------- colors (dark glass)
    private static final int PILL = 0xC80B0E12;
    private static final int PILL_SOFT = 0x9F0B0E12;
    private static final int TXT = 0xFFEDF2F5;
    private static final int DIM = 0xFF848C96;
    private static final int ACCENT = 0xFF8DC5FD;
    private static final int GOOD = 0xFF7FD98B;
    private static final int WARN = 0xFFE0B26B;
    private static final int BAD = 0xFFE57C7C;

    // TPS estimate from world-time updates
    private long lastWorldTime = -1, lastWorldStamp;
    private float tps = -1.0F;
    // blocks-per-second
    private double lastX, lastZ;
    private float bps;
    // notification toasts
    private static final class Toast { String text; long born; Toast(String t, long b) { text = t; born = b; } }
    private final List<Toast> toasts = new ArrayList<>();

    public HaloHudModule() {
        super("halo_hud", "Halo HUD", "Инфо-пилюля, сервер и TPS, список модулей, персонал FunTime, зелья, кулдауны, таргет, коорды+bps.",
                Category.HUD, true);
        on(TickEvent.class, e -> tick());
        on(HudRenderEvent.class, this::render);
        on(ModuleToggleEvent.class, e -> toast(e.module().getName() + (e.enabled() ? " · включён" : " · выключен")));
        on(WorldJoinEvent.class, e -> toast("NEXUS HUD загружен"));
    }

    private void toast(String text) {
        toasts.add(new Toast(text, System.currentTimeMillis()));
        if (toasts.size() > 4) toasts.remove(0);
    }

    private void tick() {
        if (mc.player == null) return;
        // TPS: server syncs world time periodically; tick rate = advanced world ticks per real second
        long now = Util_ms();
        long wt = mc.world.getTime();
        if (lastWorldTime >= 0) {
            long dT = wt - lastWorldTime;
            long dR = now - lastWorldStamp;
            if (dT > 0 && dR > 200) {
                float raw = Math.min(20.0F, Math.max(0.0F, dT * 1000.0F / (float) dR));
                tps = tps < 0 ? raw : tps * 0.85F + raw * 0.15F;
                lastWorldTime = wt; lastWorldStamp = now;
            }
        } else {
            lastWorldTime = wt; lastWorldStamp = now;
        }
        // blocks per second on ground plane
        double dx = mc.player.getX() - lastX, dz = mc.player.getZ() - lastZ;
        lastX = mc.player.getX(); lastZ = mc.player.getZ();
        bps = Math.max(0.0F, bps * 0.82F + (float) Math.hypot(dx, dz) * 20.0F * 0.18F);
    }

    private static long Util_ms() { return System.currentTimeMillis(); }

    // ------------------------------------------------------------------------------------------------ render

    private void render(HudRenderEvent e) {
        if (mc.player == null || mc.getDebugHud().shouldShowDebugHud()) return;
        DrawContext ctx = e.context;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float k = Math.min(1.0F, Math.min(sw / 800.0F, 1.0F)); // shrink a little on narrow windows
        int w10 = 10;

        float infoW = infoPill(ctx, w10, w10);
        serverPill(ctx, w10 + 8, w10 + 22);
        float modY = modList(ctx, w10, w10 + 48);
        potions(ctx, sw, w10 + 24);
        cooldowns(ctx, sw, w10 + 24 + potionsH + 8);
        targetHud(ctx, sw, sh);
        coordsPill(ctx, w10, sh - 16);
        toasts(ctx, sw, sh);
    }

    // ---------------------------------------------------------------- top-left

    private int potionsH = 0;

    private float infoPill(DrawContext ctx, float x, float y) {
        String name = mc.getSession().getUsername();
        String fps = mc.getCurrentFps() + "fps";
        int lat = latency();
        String ping = (lat >= 0 ? lat : "—") + "ms";
        String time = LocalTime.now().format(CLOCK);
        int nw = TextRender.widthRaw(name);
        int txtW = nw + 6 + TextRender.widthRaw(fps) + 12 + TextRender.widthRaw(ping) + 12 + TextRender.widthRaw(time);
        float hgt = 17.0F, av = 11.0F;
        float w = av + 8 + txtW + 10;
        Render2D.roundedRect(ctx, x, y, w, hgt, 6.5F, PILL);
        boolean face = drawHead(ctx, x + 4, y + (hgt - av) / 2, av, null);
        if (!face) Render2D.roundedRect(ctx, x + 4, y + (hgt - av) / 2, av, av, 3.0F, ACCENT);
        float tx = x + 4 + av + 5;
        TextRender.draw(ctx, Text.literal(name).setStyle(net.minecraft.text.Style.EMPTY.withBold(true)), tx, y + 5, TXT, false);
        tx += nw + 8;
        TextRender.draw(ctx, Text.literal(fps), tx, y + 5, DIM, false); tx += TextRender.widthRaw(fps) + 12;
        TextRender.draw(ctx, Text.literal(ping), tx, y + 5, lat >= 0 && lat < 120 ? GOOD : WARN, false); tx += TextRender.widthRaw(ping) + 12;
        TextRender.draw(ctx, Text.literal(time), tx, y + 5, DIM, false);
        return w;
    }

    private int latency() {
        try {
            if (mc.getNetworkHandler() != null && mc.player != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) return e.getLatency();
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    private float serverPill(DrawContext ctx, float x, float y) {
        ServerInfo cur = mc.getCurrentServerEntry();
        String addr = cur != null ? cur.address : null;
        if (addr == null) return 0;
        String tpsTxt = tps >= 0 ? String.format(Locale.ROOT, "%.1ftps", tps) : "···";
        int aw = TextRender.widthRaw(addr), tw = TextRender.widthRaw(tpsTxt);
        float w = aw + 10 + tw + 14;
        Render2D.roundedRect(ctx, x, y, w, 15.0F, 6.0F, PILL_SOFT);
        Render2D.circle(ctx, x + 8, y + 7.5F, 2.2F, GOOD);
        TextRender.draw(ctx, Text.literal(addr), x + 14, y + 4.5F, DIM, false);
        TextRender.draw(ctx, Text.literal(tpsTxt), x + 14 + aw + 10, y + 4.5F, TXT, false);
        return w;
    }

    // ---------------------------------------------------------------- left column

    private float modList(DrawContext ctx, float x, float y) {
        List<Module> list = new ArrayList<>();
        for (Module m : Nexus.modules().all()) {
            if (m.isEnabled() && m != this && m.getCategory() != Category.HUD) list.add(m);
        }
        float hgt = 14.0F, cy = y;
        for (Module m : list) {
            String name = m.getName();
            String mode = m.getCategory().name().toLowerCase(Locale.ROOT).substring(0, 1).toUpperCase(Locale.ROOT)
                    + m.getCategory().name().toLowerCase(Locale.ROOT).substring(1);
            int nw = TextRender.widthRaw(name), mw = TextRender.widthRaw(mode);
            float w = 16 + nw + 6 + mw + 8;
            Render2D.roundedRect(ctx, x, cy, w, hgt, 5.5F, PILL_SOFT);
            Render2D.circle(ctx, x + 6.5F, cy + hgt / 2, 1.8F, ACCENT);
            TextRender.draw(ctx, Text.literal(name), x + 12, cy + 4, TXT, false);
            TextRender.draw(ctx, Text.literal(mode), x + 12 + nw + 6, cy + 4, DIM, false);
            cy += hgt + 3.0F;
        }
        return cy;
    }

    // ---------------------------------------------------------------- top-right

    private void potions(DrawContext ctx, int sw, float y) {
        List<StatusEffectInstance> fx = new ArrayList<>();
        try {
            for (StatusEffectInstance inst : mc.player.getStatusEffects()) fx.add(inst);
        } catch (Throwable ignored) { }
        potionsH = 0;
        if (fx.isEmpty()) return;
        String head = "Зелья";
        int hw = TextRender.widthRaw(head);
        float wMax = hw + 60;
        for (StatusEffectInstance inst : fx) {
            String nm = inst.getEffectType().value().getName().getString() + ampSuffix(inst.getAmplifier());
            wMax = Math.max(wMax, 14 + TextRender.widthRaw(nm) + 20 + 30.0F);
        }
        float hgt = 13.0F;
        float x = sw - 8 - wMax;
        potionsH = Math.round(hgt + fx.size() * (hgt + 2) + 4);
        Render2D.roundedRect(ctx, x, y, wMax, potionsH, 6.0F, PILL);
        TextRender.draw(ctx, Text.literal(head).setStyle(net.minecraft.text.Style.EMPTY.withBold(true)), x + 8, y + 4, TXT, false);
        TextRender.draw(ctx, Text.literal("Время"), x + wMax - 8 - TextRender.widthRaw("Время"), y + 4, DIM, false);
        float cy = y + hgt + 1;
        for (StatusEffectInstance inst : fx) {
            int col = inst.getEffectType().value().getColor();
            Render2D.circle(ctx, x + 10, cy + 4.5F, 2.4F, 0xFF000000 | (col & 0xFFFFFF));
            String nm = inst.getEffectType().value().getName().getString() + ampSuffix(inst.getAmplifier());
            String dur = StatusEffectUtil.getDurationText(inst, 1.0F, 20).getString();
            boolean expiring = inst.getDuration() <= 100;
            TextRender.draw(ctx, Text.literal(nm), x + 16, cy, expiring ? DIM : TXT, false);
            TextRender.draw(ctx, Text.literal(dur), x + wMax - 8 - TextRender.widthRaw(dur), cy, expiring ? DIM : TXT, false);
            cy += hgt + 2;
        }
    }

    private static String ampSuffix(int amp) {
        return amp <= 0 ? "" : " " + (amp + 1);
    }

    private void cooldowns(DrawContext ctx, int sw, float y0) {
        float y = y0;
        List<String[]> rows = new ArrayList<>();
        try {
            ItemCooldownManager mgr = mc.player.getItemCooldownManager();
            if (mgr != null) {
                Map<Identifier, ?> map = ((ItemCooldownAccessor) mgr).nexus$entries();
                int now = ((ItemCooldownAccessor) mgr).nexus$tick();
                for (Map.Entry<Identifier, ?> en : map.entrySet()) {
                    int left = ((dev.nexus.client.mixin.ItemCooldownEntryAccessor) en.getValue()).nexus$end() - now;
                    if (left <= 0) continue;
                    String id = en.getKey().getPath();
                    String label = cooldownLabel(id);
                    if (label == null) continue;
                    rows.add(new String[]{label, id, formatClock(left / 20.0F)});
                }
            }
        } catch (Throwable ignored) { }
        if (rows.isEmpty()) return;
        String head = "Cooldowns";
        float wMax = TextRender.widthRaw(head) + 62;
        for (String[] r : rows) wMax = Math.max(wMax, 14 + TextRender.widthRaw(r[0]) + 20 + TextRender.widthRaw(r[2]) + 14.0F);
        float hgt = 13.0F;
        float x = sw - 8 - wMax;
        Render2D.roundedRect(ctx, x, y, wMax, hgt + rows.size() * (hgt + 2) + 4, 6.0F, PILL);
        TextRender.draw(ctx, Text.literal(head).setStyle(net.minecraft.text.Style.EMPTY.withBold(true)), x + 8, y + 4, TXT, false);
        TextRender.draw(ctx, Text.literal("Time"), x + wMax - 8 - TextRender.widthRaw("Time"), y + 4, DIM, false);
        float cy = y + hgt + 1;
        for (String[] r : rows) {
            Render2D.circle(ctx, x + 10, cy + 4.5F, 2.4F, cooldownColor(r[1]));
            String leftTxt = r[2].startsWith("0:0") ? r[2] : r[2];
            boolean hot = !r[2].equals("0:00");
            TextRender.draw(ctx, Text.literal(r[0]), x + 16, cy, hot ? TXT : DIM, false);
            TextRender.draw(ctx, Text.literal(leftTxt), x + wMax - 8 - TextRender.widthRaw(leftTxt), cy, hot ? TXT : DIM, false);
            cy += hgt + 2;
        }
    }

    private static String cooldownLabel(String path) {
        if (path.equals("ender_pearl")) return "Ender Pearl";
        if (path.equals("golden_apple") || path.equals("enchanted_golden_apple")) return "Golden Apple";
        if (path.equals("chorus_fruit")) return "Chorus Fruit";
        if (path.equals("totem_of_undying")) return "Totem";
        return null;
    }

    private static int cooldownColor(String path) {
        return switch (path) {
            case "ender_pearl" -> 0xFF3EAA9C;
            case "golden_apple", "enchanted_golden_apple" -> 0xFFE0C23E;
            case "chorus_fruit" -> 0xFFB98FED;
            default -> ACCENT;
        };
    }

    private static String formatClock(double seconds) {
        int s = (int) Math.ceil(seconds);
        return "0:" + (s < 10 ? "0" + s : s);
    }

    // ---------------------------------------------------------------- target + crosshair chips

    private void targetHud(DrawContext ctx, int sw, int sh) {
        LivingEntity ent = mc.targetedEntity instanceof LivingEntity le ? le : null;
        if (ent == null || ent.isInvisible()) return;
        String name = ent.getName().getString();
        float hp = Math.max(0.0F, ent.getHealth());
        float max = Math.max(1.0F, ent.getMaxHealth());
        // chips above: armor + totem-in-hands count
        int armor = ent.getArmor();
        int totems = 0;
        if (ent instanceof PlayerEntity pe) {
            if (pe.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) totems++;
            if (pe.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) totems++;
        }
        int nw = TextRender.widthRaw(name);
        String hpTxt = String.format(Locale.ROOT, "%.1fhp", hp);
        int hw = TextRender.widthRaw(hpTxt);
        float pw = Math.max(110.0F, 24 + nw + 18 + hw + 8);
        float px = sw / 2.0F - pw / 2.0F;
        float py = sh * 0.62F;
        // chips row
        chipIcon(ctx, sw / 2.0F, py - 12.0F, armor, totems);

        Render2D.roundedRect(ctx, px, py, pw, 20.0F, 7.0F, PILL);
        // head for players, generic icon otherwise
        if (ent instanceof PlayerEntity pe) {
            boolean face = drawHeadOf(ctx, px + 4, py + 4, 12, pe.getGameProfile());
            if (!face) Render2D.roundedRect(ctx, px + 4, py + 4, 12, 12, 3.0F, 0xFF2B323C);
        } else {
            Render2D.roundedRect(ctx, px + 4, py + 4, 12, 12, 3.0F, 0xFF2B323C);
        }
        float barW = pw - 24 - 8;
        TextRender.draw(ctx, Text.literal(name).setStyle(net.minecraft.text.Style.EMPTY.withBold(true)), px + 20, py + 3.5F, TXT, false);
        TextRender.draw(ctx, Text.literal(hpTxt), px + pw - 4 - hw, py + 3.5F, TXT, false);
        float frac = Math.max(0.0F, Math.min(1.0F, hp / max));
        Render2D.roundedRect(ctx, px + 20, py + 13.0F, barW, 3.0F, 1.5F, 0xFF232830);
        Render2D.roundedRect(ctx, px + 20, py + 13.0F, barW * frac, 3.0F, 1.5F, hpCol(frac));
    }

    private void chipIcon(DrawContext ctx, float cx, float cy, int armor, int totems) {
        int cnt = 1 + (totems > 0 ? 1 : 0);
        float w = cnt * 14 + 4;
        float x = cx - w / 2.0F;
        Render2D.roundedRect(ctx, x, cy - 2, w, 12, 5.0F, PILL_SOFT);
        TextRender.draw(ctx, Text.literal("⛨" + armor), x + 4, cy + 1, DIM, false);
        if (totems > 0) TextRender.draw(ctx, Text.literal("☻" + totems), x + 18, cy + 1, WARN, false);
    }

    private static int hpCol(float frac) {
        return frac > 0.55F ? GOOD : frac > 0.25F ? WARN : BAD;
    }

    private boolean drawHead(DrawContext ctx, float x, float y, float size, java.util.UUID ignore) {
        return drawHeadOf(ctx, x, y, size, mc.getGameProfile());
    }

    private boolean drawHeadOf(DrawContext ctx, float x, float y, float size, com.mojang.authlib.GameProfile profile) {
        try {
            java.util.function.Supplier<SkinTextures> skin = mc.getSkinProvider().supplySkinTextures(profile, false);
            SkinTextures tex = skin != null ? skin.get() : null;
            if (tex != null) {
                PlayerSkinDrawer.draw(ctx, tex, Math.round(x), Math.round(y), Math.round(size));
                return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    // ---------------------------------------------------------------- bottom rows

    private void coordsPill(DrawContext ctx, float x, float y) {
        if (mc.player == null) return;
        String s = Math.round(mc.player.getX()) + "x · " + Math.round(mc.player.getY()) + "y · "
                + Math.round(mc.player.getZ()) + "z";
        String v = String.format(Locale.ROOT, "%.1fbps", bps);
        float w = 12 + TextRender.widthRaw(s) + 10 + TextRender.widthRaw(v) + 4;
        Render2D.roundedRect(ctx, x, y, w, 14.0F, 6.0F, PILL_SOFT);
        Render2D.rect(ctx, x + 5, y + 4, 4, 6, ACCENT);
        TextRender.draw(ctx, Text.literal(s), x + 13, y + 4.5F, DIM, false);
        TextRender.draw(ctx, Text.literal(v), x + 13 + TextRender.widthRaw(s) + 10, y + 4.5F, TXT, false);
    }

    private void toasts(DrawContext ctx, int sw, int sh) {
        long now = Util_ms();
        float y = sh - 22.0F;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            long age = now - t.born;
            if (age > 3800) { toasts.remove(i); continue; }
            float a = age < 180 ? age / 180.0F : age > 3400 ? 1.0F - (age - 3400) / 400.0F : 1.0F;
            Render2D.pushAlpha(a);
            int tw = TextRender.widthRaw(t.text);
            float w = tw + 22;
            Render2D.roundedRect(ctx, sw - 8 - w, y, w, 14.0F, 6.0F, PILL);
            Render2D.circle(ctx, sw - 8 - w + 8, y + 7, 2.2F, ACCENT);
            TextRender.draw(ctx, Text.literal(t.text), sw - 8 - w + 14, y + 4.5F, TXT, false);
            Render2D.popAlpha();
            y -= 17.0F;
        }
    }
}
