package dev.nexus.client.module.impl.hud;

import dev.nexus.client.event.events.AttackEntityEvent;
import dev.nexus.client.event.events.TickEvent;
import dev.nexus.client.event.events.WorldLeaveEvent;
import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.ui.animation.Animation;
import dev.nexus.client.ui.animation.Easing;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Information about the entity you attacked or are looking at (only entities your client already sees).
 */
public final class TargetHudModule extends HudModule {
    private static final float WIDTH = 124f, HEIGHT = 36f;

    private final ModeSetting source = add(new ModeSetting("source", "Target source",
            "Which entity is shown", "Both", "Both", "Attacked", "Crosshair"));
    private final NumberSetting timeout = add(new NumberSetting("timeout", "Hide after",
            "Hide the panel after this many seconds without contact", 4, 1, 15, 0.5).suffix("s"));
    private final BooleanSetting head = add(new BooleanSetting("head", "Portrait", "Show the player's face or a mob icon", true));
    private final BooleanSetting distance = add(new BooleanSetting("distance", "Distance", "Show distance to the target", true));
    private final BooleanSetting armor = add(new BooleanSetting("armor", "Armor", "Show the target's armor points", true));

    private LivingEntity target;
    private long lastContact;
    private final Animation visibility = new Animation(0f, 220, Easing.OUT_CUBIC);
    private final SmoothValue healthBar = new SmoothValue(1f, 10f);
    private final SmoothValue damageBar = new SmoothValue(1f, 3f);
    private final Map<EntityType<?>, ItemStack> eggs = new HashMap<>();
    private Entity cachedNameFor;
    private String cachedName = "";
    private int cachedHpTenths = -1, cachedArmor = -1, cachedDist = -1;
    private String hpText = "", infoText = "";

    public TargetHudModule() {
        super("target_hud", "Target HUD", "Shows health, armor and distance of the entity you fight or look at.",
                false, CENTER, CENTER, 0, 40);
        on(AttackEntityEvent.class, e -> {
            if (!source.is("Crosshair") && e.target() instanceof LivingEntity living) setTarget(living);
        });
        on(TickEvent.class, e -> tick());
        on(WorldLeaveEvent.class, e -> clear());
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void clear() {
        target = null;
        cachedNameFor = null;
        visibility.set(0f);
    }

    private void setTarget(LivingEntity living) {
        if (living != target) {
            float ratio = ratio(living);
            healthBar.snap(ratio);
            damageBar.snap(ratio);
        }
        target = living;
        lastContact = System.currentTimeMillis();
    }

    private void tick() {
        if (mc.player == null) return;
        if (!source.is("Attacked") && mc.targetedEntity instanceof LivingEntity living && living.isAlive()) setTarget(living);
        if (target == null) return;
        boolean gone = target.isRemoved() || target.getEntityWorld() != mc.world || target.distanceTo(mc.player) > 64;
        boolean expired = System.currentTimeMillis() - lastContact > timeout.get() * 1000;
        if (gone || expired || (target.isDead() && System.currentTimeMillis() - lastContact > 1200)) visibility.animateTo(0f);
        else visibility.animateTo(1f);
        if (visibility.target() == 0f && visibility.isFinished()) target = null;
    }

    private static float ratio(LivingEntity e) {
        float max = e.getMaxHealth();
        return max <= 0 ? 0 : Math.max(0, Math.min(1, e.getHealth() / max));
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        LivingEntity t = target;
        float vis = visibility.get();
        if (editor && (t == null || vis < 0.05f)) {
            t = mc.player; // preview with your own player
            vis = 1f;
        }
        if (t == null || vis <= 0.01f) return false;
        setSize(WIDTH, HEIGHT);
        Render2D.pushAlpha(vis);
        try {
            draw(ctx, t);
        } finally {
            Render2D.popAlpha();
        }
        return true;
    }

    private void draw(DrawContext ctx, LivingEntity t) {
        if (hasBackground()) {
            Render2D.roundedRect(ctx, 0, 0, WIDTH, HEIGHT, Math.max(3, HudStyle.radius()), HudStyle.PANEL);
        }
        float x = 5f;
        if (head.isOn()) {
            int size = 26;
            Render2D.roundedRect(ctx, 4, 4, size + 2, size + 2, 3, 0x40000000);
            if (t instanceof AbstractClientPlayerEntity player) {
                PlayerSkinDrawer.draw(ctx, player.getSkin(), 5, 5, size, Render2D.apply(0xFFFFFFFF));
            } else {
                ItemStack egg = eggs.computeIfAbsent(t.getType(), type -> {
                    SpawnEggItem item = SpawnEggItem.forEntity(type);
                    return item == null ? ItemStack.EMPTY : new ItemStack(item);
                });
                if (!egg.isEmpty()) {
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(5 + size / 2f - 12, 5 + size / 2f - 12);
                    ctx.getMatrices().scale(1.5f, 1.5f);
                    ctx.drawItem(egg, 0, 0);
                    ctx.getMatrices().popMatrix();
                } else {
                    String letter = cachedName.isEmpty() ? "?" : cachedName.substring(0, 1);
                    TextRender.drawScaled(ctx, letter, 5 + size / 2f - TextRender.width(letter), 5 + size / 2f - 8, 2f, Theme.TEXT, false);
                }
            }
            x = 5 + size + 6;
        }
        // texts (cached until the values change)
        if (cachedNameFor != t) {
            cachedNameFor = t;
            cachedName = TextRender.ellipsize(t.getDisplayName().getString(), (int) (WIDTH - x - 6));
            cachedHpTenths = cachedArmor = cachedDist = -1;
        }
        float hp = t.getHealth() + t.getAbsorptionAmount();
        int hpTenths = Math.round(hp * 10);
        int armorPts = t.getArmor();
        int dist = mc.player == null ? 0 : Math.round(t.distanceTo(mc.player) * 10);
        if (hpTenths != cachedHpTenths) {
            cachedHpTenths = hpTenths;
            hpText = String.format(Locale.ROOT, "%.1f\u2764", hpTenths / 20f);
        }
        if (armorPts != cachedArmor || dist != cachedDist) {
            cachedArmor = armorPts;
            cachedDist = dist;
            StringBuilder sb = new StringBuilder();
            if (armor.isOn() && armorPts > 0) sb.append(armorPts).append(" armor");
            if (distance.isOn() && t != mc.player) {
                if (!sb.isEmpty()) sb.append("  ");
                sb.append(String.format(Locale.ROOT, "%.1fm", dist / 10f));
            }
            infoText = sb.toString();
        }
        HudStyle.text(ctx, cachedName, x, 5, HudStyle.VALUE);
        float hpW = TextRender.width(hpText);
        HudStyle.text(ctx, hpText, WIDTH - 6 - hpW, 5, 0xFFFF7A8A);
        if (!infoText.isEmpty()) HudStyle.text(ctx, infoText, x, 15, HudStyle.LABEL);

        // health bar with delayed "damage" trail
        float ratio = ratio(t);
        float bar = healthBar.update(ratio);
        float trail = damageBar.update(ratio);
        float bw = WIDTH - x - 6, by = HEIGHT - 9, bh = 4;
        Render2D.roundedRect(ctx, x, by, bw, bh, 2, 0x50000000);
        if (trail > bar) Render2D.roundedRect(ctx, x, by, bw * trail, bh, 2, 0x90FFFFFF);
        int c1 = ratio > 0.5f ? ColorUtil.lerp(0xFFFFE066, 0xFF5DF08A, (ratio - 0.5f) * 2) : ColorUtil.lerp(0xFFFF5A5A, 0xFFFFE066, ratio * 2);
        Render2D.roundedGradient(ctx, x, by, Math.max(bh, bw * bar), bh, 2, ColorUtil.shade(c1, 0.8f), c1, true);
    }
}
