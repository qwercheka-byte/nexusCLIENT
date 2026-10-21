package dev.nexus.client.module.impl.hud;

import dev.nexus.client.event.events.HudRenderEvent;
import dev.nexus.client.event.events.ModuleToggleEvent;
import dev.nexus.client.event.events.TickEvent;
import dev.nexus.client.event.events.WorldJoinEvent;
import dev.nexus.client.mixin.ItemCooldownAccessor;
import dev.nexus.client.mixin.ItemCooldownEntryAccessor;
import dev.nexus.client.Nexus;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.waypoint.Waypoint;
import dev.nexus.client.waypoint.WaypointStore;
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
 * Dark-minimal "night survival" HUD pack (reference: thin white lines, translucent dark panels,
 * small sans-serif, dark-gray/white palette only):
 *  - top-left  : one translucent panel with vitals + active effects + item cooldowns rows,
 *  - top-right : stacked stat blocks (nick/fps/ping/clock · server+tps · coordinates+bps),
 *  - bottom-centre: floating chip with the NEAREST waypoint name and distance,
 *  - bottom-left : single tiny technical text line (client version, fps),
 *  - centre      : minimal target chip (name + thin hp bar, armor/totems inline),
 *  - bottom-right: small notification toasts.
 * Everything is read from vanilla client state — nothing is sent to the server.
 */
public final class HaloHudModule extends Module {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    // ------------------------------------------------- palette: dark glass + thin white lines
    private static final int PANEL  = 0xA8111216;
    private static final int BORDER = 0x2EFFFFFF;
    private static final int TXT    = 0xFFF0F1F3;
    private static final int DIM    = 0xFF9BA1A8;
    private static final int FAINT  = 0xFF5C636B;
    private static final int LINE   = 0x22FFFFFF;
    private static final int BAD    = 0xFFE07A7A;

    // TPS estimate from world-time updates
    private long lastWorldTime = -1, lastWorldStamp;
    private float tps = -1.0F;
    // blocks-per-second
    private double lastX, lastZ;
    private float bps;
    // notification toasts
    private static final class Toast { String text; long born; Toast(String t, long b) { text = t; born = b; } }
    private final List<Toast> toasts = new ArrayList<>();

    private final BooleanSetting hideVanilla = add(new BooleanSetting(
            "hide-vanilla", "Скрывать ванильный HUD", "Убирает хотбар, сердечки, голод, полосу опыта и подсказки WASD — всё в панелях Halo", true));
    private final BooleanSetting autoHideDupes = add(new BooleanSetting(
            "auto-hide-dupes", "Прятать дубли HUD", "Выключать старые HUD-модули (FPS, координаты, зелья, таргет), когда Halo включён", true));
    private boolean dupesCleaned;

    public boolean hidesVanilla() { return hideVanilla.isOn(); }

    public HaloHudModule() {
        super("halo_hud", "Halo HUD", "Тёмный минимализм: статы слева, статистика+координаты справа, вейпоинт-чип снизу, тосты.",
                Category.HUD, true);
        on(TickEvent.class, e -> tick());
        on(HudRenderEvent.class, this::render);
        on(ModuleToggleEvent.class, e -> toast(e.module().getName() + (e.enabled() ? " · включён" : " · выключен")));
        on(WorldJoinEvent.class, e -> toast("NEXUS HUD загружен"));
    }

    @Override
    protected void onEnable() {
        dupesCleaned = false;
    }

    /** Turns off the older HUD modules whose content Halo already covers. Retried on tick because the
     *  module registry may not be fully live yet during start-up. */
    @SuppressWarnings("unchecked")
    private void cleanDuplicates() {
        if (Nexus.modules() == null) return;
        dupesCleaned = true;
        if (!autoHideDupes.isOn()) return;
        Class<? extends Module>[] dupes = new Class[]{
                FpsHudModule.class, CoordinatesHudModule.class, PotionHudModule.class,
                ArmorHudModule.class, TargetHudModule.class, WorldTimeHudModule.class, ModuleListHudModule.class};
        boolean any = false;
        for (Class<? extends Module> cls : dupes) {
            Module m = Nexus.modules().get(cls);
            if (m != null && m.isEnabled()) { m.setEnabled(false); any = true; }
        }
        if (any) toast("Старые дубли HUD выключены — можно вернуть в меню");
    }

    private void toast(String text) {
        toasts.add(new Toast(text, System.currentTimeMillis()));
        if (toasts.size() > 4) toasts.remove(0);
    }

    private void tick() {
        if (!dupesCleaned) cleanDuplicates();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
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
        double dx = mc.player.getX() - lastX, dz = mc.player.getZ() - lastZ;
        lastX = mc.player.getX(); lastZ = mc.player.getZ();
        bps = Math.max(0.0F, bps * 0.82F + (float) Math.hypot(dx, dz) * 20.0F * 0.18F);
    }

    // ------------------------------------------------------------------------------------------------ render

    private void render(HudRenderEvent e) {
        if (mc.player == null || mc.getDebugHud().shouldShowDebugHud()) return;
        DrawContext ctx = e.context;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        vitalsPanel(ctx, 8, 8);
        statBlocks(ctx, sw, 8);
        targetChip(ctx, sw, sh);
        waypointChip(ctx, sw, sh);
        techLine(ctx, 8, sh - 12);
        toasts(ctx, sw, sh);
    }

    // ---------------------------------------------------------------- panel primitive

    private void panel(DrawContext ctx, float x, float y, float w, float h) {
        Render2D.roundedRect(ctx, x, y, w, h, 5.0F, PANEL);
        Render2D.roundedOutline(ctx, x, y, w, h, 5.0F, 0.6F, BORDER);
    }

    // ---------------------------------------------------------------- top-left: vitals list

    private void vitalsPanel(DrawContext ctx, float x, float y) {
        List<Row> rows = new ArrayList<>();
        // vitals (icon + label + value)
        float hp = mc.player.getHealth(), hpMax = mc.player.getMaxHealth();
        rows.add(new Row(Icon.HEART, "Здоровье", fmt1(hp) + "/" + Math.round(hpMax), hp / hpMax < 0.3F ? BAD : TXT));
        rows.add(new Row(Icon.HUNGER, "Сытость", mc.player.getHungerManager().getFoodLevel() + "/20", TXT));
        int armor = mc.player.getArmor();
        rows.add(new Row(Icon.SHIELD, "Броня", armor + "/20", armor <= 0 ? FAINT : TXT));
        int xp = mc.player.experienceLevel;
        if (xp > 0) rows.add(new Row(Icon.XP, "Уровень", String.valueOf(xp), TXT));
        int air = mc.player.getAir(), airMax = mc.player.getMaxAir();
        if (air < airMax) rows.add(new Row(Icon.BUBBLE, "Кислород", String.format(Locale.ROOT, "%.0f%%", air * 100.0F / airMax), BAD));
        // effects
        List<String[]> effects = new ArrayList<>();
        try {
            for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
                String nm = inst.getEffectType().value().getName().getString() + ampSuffix(inst.getAmplifier());
                String dur = StatusEffectUtil.getDurationText(inst, 1.0F, 20).getString();
                effects.add(new String[]{nm, dur});
            }
        } catch (Throwable ignored) { }
        // cooldowns
        List<String[]> cools = new ArrayList<>();
        try {
            ItemCooldownManager mgr = mc.player.getItemCooldownManager();
            if (mgr != null) {
                Map<Identifier, ?> map = ((ItemCooldownAccessor) mgr).nexus$entries();
                int now = ((ItemCooldownAccessor) mgr).nexus$tick();
                for (Map.Entry<Identifier, ?> en : map.entrySet()) {
                    int left = ((ItemCooldownEntryAccessor) en.getValue()).nexus$end() - now;
                    if (left <= 0) continue;
                    String label = cooldownLabel(en.getKey().getPath());
                    if (label != null) cools.add(new String[]{label, formatClock(left / 20.0F)});
                }
            }
        } catch (Throwable ignored) { }

        boolean hasExtra = !effects.isEmpty() || !cools.isEmpty();
        float rowH = 10.5F, padX = 7.0F;
        // width: icon(6)+gap ~ 16 + label + gap 12 + value + padX*2
        float labelW = 0, valW = 0;
        for (Row r : rows) { labelW = Math.max(labelW, TextRender.widthRaw(r.label)); valW = Math.max(valW, TextRender.widthRaw(r.value)); }
        for (String[] r : effects) { labelW = Math.max(labelW, TextRender.widthRaw(r[0])); valW = Math.max(valW, TextRender.widthRaw(r[1])); }
        for (String[] r : cools) { labelW = Math.max(labelW, TextRender.widthRaw(r[0])); valW = Math.max(valW, TextRender.widthRaw(r[1])); }
        float w = padX + 6 + 6 + labelW + 14 + valW + padX;
        int totalRows = rows.size() + (hasExtra ? effects.size() + cools.size() : 0);
        float h = 8 + rows.size() * rowH + (hasExtra ? 5.5F + (effects.size() + cools.size()) * rowH : 0) + 4;

        panel(ctx, x, y, w, h);
        float cy = y + 6;
        float tx = x + padX + 6 + 6;
        float vx = x + w - padX;
        for (Row r : rows) {
            drawIcon(ctx, r.icon, x + padX + 3, cy + 5.25F, r.color == FAINT ? FAINT : TXT);
            TextRender.draw(ctx, Text.literal(r.label), tx, cy + 1.5F, DIM, false);
            TextRender.draw(ctx, Text.literal(r.value), vx - TextRender.widthRaw(r.value), cy + 1.5F, r.color, false);
            cy += rowH;
        }
        if (hasExtra) {
            cy += 1.5F;
            Render2D.line(ctx, x + padX, cy, x + w - padX, cy, 0.6F, LINE);
            cy += 4;
            for (String[] r : effects) {
                Render2D.circle(ctx, x + padX + 3, cy + 5.25F, 1.8F, TXT);
                TextRender.draw(ctx, Text.literal(r[0]), tx, cy + 1.5F, TXT, false);
                TextRender.draw(ctx, Text.literal(r[1]), vx - TextRender.widthRaw(r[1]), cy + 1.5F, DIM, false);
                cy += rowH;
            }
            for (String[] r : cools) {
                drawIcon(ctx, Icon.CLOCK, x + padX + 3, cy + 5.25F, FAINT);
                TextRender.draw(ctx, Text.literal(r[0]), tx, cy + 1.5F, FAINT, false);
                TextRender.draw(ctx, Text.literal(r[1]), vx - TextRender.widthRaw(r[1]), cy + 1.5F, DIM, false);
                cy += rowH;
            }
        }
    }

    private enum Icon { HEART, HUNGER, SHIELD, XP, BUBBLE, CLOCK }
    private record Row(Icon icon, String label, String value, int color) { }

    /** Tiny thin-line white vector icons, ~5px, stroke look. */
    private static void drawIcon(DrawContext ctx, Icon ic, float cx, float cy, int color) {
        switch (ic) {
            case HEART -> { // two small circles + a tiny triangle tip
                Render2D.circle(ctx, cx - 1.3F, cy - 0.8F, 1.5F, color);
                Render2D.circle(ctx, cx + 1.3F, cy - 0.8F, 1.5F, color);
                Render2D.line(ctx, cx - 2.4F, cy + 0.1F, cx, cy + 2.6F, 0.8F, color);
                Render2D.line(ctx, cx + 2.4F, cy + 0.1F, cx, cy + 2.6F, 0.8F, color);
            }
            case HUNGER -> { // drumstick: circle + bone hint
                Render2D.circle(ctx, cx - 0.8F, cy - 0.8F, 1.8F, color);
                Render2D.line(ctx, cx - 0.2F, cy + 0.2F, cx + 2.0F, cy + 2.4F, 0.9F, color);
            }
            case SHIELD -> { // shield outline stylization: top line + two slopes
                Render2D.line(ctx, cx - 2.3F, cy - 2.0F, cx + 2.3F, cy - 2.0F, 0.8F, color);
                Render2D.line(ctx, cx - 2.3F, cy - 2.0F, cx, cy + 2.6F, 0.8F, color);
                Render2D.line(ctx, cx + 2.3F, cy - 2.0F, cx, cy + 2.6F, 0.8F, color);
            }
            case XP -> { // star-ish: plus + diagonals dot
                Render2D.line(ctx, cx - 2.0F, cy, cx + 2.0F, cy, 0.8F, color);
                Render2D.line(ctx, cx, cy - 2.0F, cx, cy + 2.0F, 0.8F, color);
            }
            case BUBBLE -> Render2D.ring(ctx, cx, cy, 2.0F, 0.8F, color);
            case CLOCK -> { // clock: ring + hands
                Render2D.ring(ctx, cx, cy, 2.1F, 0.7F, color);
                Render2D.line(ctx, cx, cy, cx, cy - 1.4F, 0.7F, color);
                Render2D.line(ctx, cx, cy, cx + 1.0F, cy + 0.5F, 0.7F, color);
            }
        }
    }

    // ---------------------------------------------------------------- top-right: stat blocks

    private void statBlocks(DrawContext ctx, int sw, float y) {
        float bh = 16.0F, gap = 3.0F, cy = y;
        // block 1: nick · fps · ping · clock
        String name = mc.getSession().getUsername();
        String fps = mc.getCurrentFps() + "fps";
        int lat = latency();
        String ping = (lat >= 0 ? lat : "—") + "ms";
        String time = LocalTime.now().format(CLOCK);
        float w1 = block(ctx, sw, cy, new String[]{name, fps, ping, time}, new int[]{TXT, DIM, lat >= 0 && lat < 120 ? DIM : BAD, DIM});
        cy += bh + gap;
        // block 2: server + tps (servers only)
        ServerInfo cur = mc.getCurrentServerEntry();
        if (cur != null && cur.address != null) {
            String tpsTxt = tps >= 0 ? String.format(Locale.ROOT, "%.1ftps", tps) : "···";
            block(ctx, sw, cy, new String[]{cur.address, tpsTxt}, new int[]{DIM, TXT});
            cy += bh + gap;
        }
        // block 3: coords + bps
        String coords = String.format(Locale.ROOT, "%.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        String spd = String.format(Locale.ROOT, "%.1fbps", bps);
        block(ctx, sw, cy, new String[]{coords, spd}, new int[]{TXT, DIM});
    }

    /** One right-aligned dark block: parts separated by thin middots. Returns width. */
    private float block(DrawContext ctx, int sw, float y, String[] parts, int[] colors) {
        float innerW = 0;
        String dot = "·";
        int dotW = TextRender.widthRaw(dot);
        for (int i = 0; i < parts.length; i++) innerW += TextRender.widthRaw(parts[i]) + (i > 0 ? dotW + 8.0F : 0);
        float w = innerW + 14.0F;
        float x = sw - 8 - w;
        panel(ctx, x, y, w, 16.0F);
        float tx = x + 7.0F;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                TextRender.draw(ctx, Text.literal(dot), tx, y + 5, FAINT, false);
                tx += dotW + 8.0F;
            }
            TextRender.draw(ctx, Text.literal(parts[i]), tx, y + 5, colors[i], false);
            tx += TextRender.widthRaw(parts[i]);
        }
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

    // ---------------------------------------------------------------- bottom-centre: nearest waypoint

    private void waypointChip(DrawContext ctx, int sw, int sh) {
        Waypoint nearest = null;
        double best = Double.MAX_VALUE;
        try {
            String dim = mc.world.getRegistryKey().getValue().toString();
            List<Waypoint> list = WaypointStore.INSTANCE.inDimension(dim);
            for (Waypoint wp : list) {
                double d = wp.distanceTo(mc.player.getX(), mc.player.getZ());
                if (d < best) { best = d; nearest = wp; }
            }
        } catch (Throwable ignored) { }
        if (nearest == null) return;
        String dist = best < 10.0 ? String.format(Locale.ROOT, "%.1fm", best) : String.format(Locale.ROOT, "%.0fm", (long) Math.round(best));
        String name = nearest.name;
        int nw = TextRender.widthRaw(name), dw = TextRender.widthRaw(dist);
        float w = 30 + nw + 10 + dw;
        float x = sw / 2.0F - w / 2.0F;
        float y = sh - 52;
        panel(ctx, x, y, w, 20.0F);
        // marker icon: diamond of thin lines
        float icx = x + 11, icy = y + 10;
        Render2D.line(ctx, icx, icy - 3.2F, icx + 2.6F, icy, 0.9F, TXT);
        Render2D.line(ctx, icx + 2.6F, icy, icx, icy + 3.2F, 0.9F, TXT);
        Render2D.line(ctx, icx, icy + 3.2F, icx - 2.6F, icy, 0.9F, TXT);
        Render2D.line(ctx, icx - 2.6F, icy, icx, icy - 3.2F, 0.9F, TXT);
        TextRender.draw(ctx, Text.literal(name), x + 19, y + 6.5F, TXT, false);
        TextRender.draw(ctx, Text.literal(dist), x + 19 + nw + 10, y + 6.5F, DIM, false);
    }

    // ---------------------------------------------------------------- bottom-left: technical line

    private void techLine(DrawContext ctx, float x, float y) {
        String ver = clientVersion();
        String line = "Nexus " + ver + " · mc 1.21.11 · " + mc.getCurrentFps() + "fps";
        TextRender.draw(ctx, Text.literal(line), x, y, FAINT, false);
    }

    private static String clientVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer("nexus").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.0.14");
        } catch (Throwable t) {
            return "1.0.14";
        }
    }

    // ---------------------------------------------------------------- centre: target chip

    private void targetChip(DrawContext ctx, int sw, int sh) {
        LivingEntity ent = mc.targetedEntity instanceof LivingEntity le ? le : null;
        if (ent == null || ent.isInvisible()) return;
        String name = ent.getName().getString();
        float hp = Math.max(0.0F, ent.getHealth());
        float max = Math.max(1.0F, ent.getMaxHealth());
        String right;
        if (ent instanceof PlayerEntity pe) {
            int totems = 0;
            if (pe.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) totems++;
            if (pe.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) totems++;
            right = String.format(Locale.ROOT, "%.0fhp · ⛨%d", hp, pe.getArmor()) + (totems > 0 ? " · " + totems : "");
        } else {
            right = String.format(Locale.ROOT, "%.0fhp", hp);
        }
        int nw = TextRender.widthRaw(name), rw = TextRender.widthRaw(right);
        float pw = 24 + nw + 16 + rw;
        float px = sw / 2.0F - pw / 2.0F;
        float py = sh * 0.60F;
        panel(ctx, px, py, pw, 20.0F);
        if (ent instanceof PlayerEntity pe) {
            boolean face = drawHeadOf(ctx, px + 4, py + 4, 12, pe.getGameProfile());
            if (!face) Render2D.roundedRect(ctx, px + 4, py + 4, 12, 12, 3.0F, 0xFF22262C);
        } else {
            Render2D.roundedRect(ctx, px + 4, py + 4, 12, 12, 3.0F, 0xFF22262C);
        }
        TextRender.draw(ctx, Text.literal(name), px + 20, py + 3.5F, TXT, false);
        TextRender.draw(ctx, Text.literal(right), px + 20 + nw + 16, py + 3.5F, DIM, false);
        float frac = Math.max(0.0F, Math.min(1.0F, hp / max));
        Render2D.roundedRect(ctx, px + 20, py + 14.5F, pw - 28, 2.0F, 1.0F, 0x55FFFFFF);
        Render2D.roundedRect(ctx, px + 20, py + 14.5F, (pw - 28) * frac, 2.0F, 1.0F, frac < 0.3F ? BAD : 0xDDFFFFFF);
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

    // ---------------------------------------------------------------- bottom-right: toasts

    private void toasts(DrawContext ctx, int sw, int sh) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.born > 4200);
        float cy = sh - 30;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            long age = now - t.born;
            float alpha = age > 3600 ? Math.max(0.0F, 1.0F - (age - 3600) / 600.0F) : 1.0F;
            int a = Math.round(alpha * 0xA8) << 24;
            int ba = Math.round(alpha * 0x2E) << 24;
            int ta = Math.round(alpha * 0xFF) << 24;
            int w = TextRender.widthRaw(t.text) + 16;
            float x = sw - 8 - w;
            Render2D.roundedRect(ctx, x, cy, w, 16.0F, 5.0F, a | 0x00111216);
            Render2D.roundedOutline(ctx, x, cy, w, 16.0F, 5.0F, 0.6F, ba | 0x00FFFFFF);
            TextRender.draw(ctx, Text.literal(t.text), x + 8, cy + 5, ta | 0x00F0F1F3, false);
            cy -= 19.0F;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String fmt1(float v) {
        return (v % 1 == 0) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String ampSuffix(int amp) {
        return amp <= 0 ? "" : " " + (amp + 1);
    }

    private static String cooldownLabel(String path) {
        if (path.equals("ender_pearl")) return "Ender Pearl";
        if (path.equals("golden_apple") || path.equals("enchanted_golden_apple")) return "Golden Apple";
        if (path.equals("chorus_fruit")) return "Chorus Fruit";
        if (path.equals("totem_of_undying")) return "Totem";
        return null;
    }

    private static String formatClock(double seconds) {
        int s = (int) Math.ceil(seconds);
        return "0:" + (s < 10 ? "0" + s : s);
    }
}
