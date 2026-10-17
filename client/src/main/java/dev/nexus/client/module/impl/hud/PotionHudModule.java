package dev.nexus.client.module.impl.hud;

import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.util.TimeFormat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compact list of active status effects with remaining time. */
public final class PotionHudModule extends HudModule {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final Comparator<StatusEffectInstance> BY_DURATION = (a, b) -> {
        if (a.isInfinite() != b.isInfinite()) return a.isInfinite() ? -1 : 1;
        return Integer.compare(b.getDuration(), a.getDuration());
    };

    private final BooleanSetting icons = add(new BooleanSetting("icons", "Icons", "Show effect icons", true));
    private final BooleanSetting showLevel = add(new BooleanSetting("level", "Show level", "Append the amplifier (II, III…)", true));
    private final BooleanSetting compact = add(new BooleanSetting("compact", "Compact", "Only icon and time", false));
    private final ModeSetting sort = add(new ModeSetting("sort", "Sort", "Order of the effects", "Duration", "Duration", "Name"));
    private final BooleanSetting hideVanilla = add(new BooleanSetting("hide_vanilla", "Hide vanilla icons",
            "Hide the vanilla effect icons in the top-right corner", true));
    private final BooleanSetting blink = add(new BooleanSetting("blink", "Blink when ending",
            "Pulse effects with less than 10 seconds left", true));

    private final List<StatusEffectInstance> effects = new ArrayList<>();
    private final Map<RegistryEntry<StatusEffect>, String[]> names = new HashMap<>();
    /** Effect icons live in the GUI atlas since 1.21.9; the sprite id is derived once per effect. */
    private final Map<RegistryEntry<StatusEffect>, Identifier> iconIds = new HashMap<>();
    private final Comparator<StatusEffectInstance> byName = Comparator.comparing(e -> name(e));
    private List<StatusEffectInstance> preview;

    public PotionHudModule() {
        super("potion_hud", "Potion HUD", "Active status effects with remaining time in a compact list.",
                false, END, CENTER, 4, 0);
    }

    public boolean hidesVanilla() {
        return hideVanilla.isOn();
    }

    private String name(StatusEffectInstance e) {
        String[] byLevel = names.computeIfAbsent(e.getEffectType(), k -> new String[ROMAN.length + 1]);
        int amp = Math.max(0, Math.min(ROMAN.length, e.getAmplifier()));
        int idx = showLevel.isOn() ? amp : ROMAN.length;
        if (byLevel[idx] == null) {
            String base = e.getEffectType().value().getName().getString();
            byLevel[idx] = showLevel.isOn() && amp < ROMAN.length ? base + ROMAN[amp]
                    : showLevel.isOn() ? base + " " + (amp + 1) : base;
        }
        return byLevel[idx];
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        effects.clear();
        if (mc.player != null) {
            for (StatusEffectInstance e : mc.player.getStatusEffects()) if (e.shouldShowIcon()) effects.add(e);
        }
        if (effects.isEmpty() && editor) {
            if (preview == null) preview = List.of(new StatusEffectInstance(StatusEffects.SPEED, 20 * 95, 1),
                    new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 42, 0),
                    new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 8, 0));
            effects.addAll(preview);
        }
        if (effects.isEmpty()) return false;
        effects.sort(sort.is("Name") ? byName : BY_DURATION);

        boolean right = isRightAligned();
        boolean showIcons = icons.isOn() || compact.isOn();
        float pad = HudStyle.PAD_X, rowH = 13f, iconSize = 11f;
        float textW = 0;
        for (StatusEffectInstance e : effects) {
            float w = TextRender.width(time(e));
            if (!compact.isOn()) w += TextRender.width(name(e)) + 6;
            textW = Math.max(textW, w);
        }
        float w = pad * 2 + (showIcons ? iconSize + 4 : 0) + textW;
        float h = HudStyle.PAD_Y * 2 + effects.size() * rowH - 2;
        setSize(w, h);
        HudStyle.panel(ctx, this, w, h);

        long now = System.currentTimeMillis();
        for (int i = 0; i < effects.size(); i++) {
            StatusEffectInstance e = effects.get(i);
            float y = HudStyle.PAD_Y + i * rowH;
            boolean pulse = blink.isOn() && !e.isInfinite() && e.getDuration() < 200;
            if (pulse) Render2D.pushAlpha(0.55f + 0.45f * (float) Math.cos(now / 160.0));
            float iconX = right ? w - pad - iconSize : pad;
            if (showIcons) {
                Identifier icon = iconIds.computeIfAbsent(e.getEffectType(), InGameHud::getEffectTexture);
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(iconX, y);
                ctx.getMatrices().scale(iconSize / 18f, iconSize / 18f);
                ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 18, 18, Render2D.apply(0xFFFFFFFF));
                ctx.getMatrices().popMatrix();
            }
            String time = time(e);
            int nameColor = e.getEffectType().value().isBeneficial() ? HudStyle.VALUE : 0xFFFF8A8A;
            float textY = y + 2;
            if (right) {
                float x = showIcons ? iconX - 4 : w - pad;
                HudStyle.text(ctx, time, x - TextRender.width(time), textY, HudStyle.LABEL);
                if (!compact.isOn()) {
                    String n = name(e);
                    HudStyle.text(ctx, n, x - TextRender.width(time) - 6 - TextRender.width(n), textY, nameColor);
                }
            } else {
                float x = showIcons ? iconX + iconSize + 4 : pad;
                if (!compact.isOn()) {
                    String n = name(e);
                    HudStyle.text(ctx, n, x, textY, nameColor);
                    x += TextRender.width(n) + 6;
                }
                HudStyle.text(ctx, time, x, textY, HudStyle.LABEL);
            }
            if (pulse) Render2D.popAlpha();
        }
        return true;
    }

    private static String time(StatusEffectInstance e) {
        return e.isInfinite() ? "\u221E" : TimeFormat.ticks(e.getDuration());
    }

    @Override
    public void onSettingChanged(dev.nexus.client.setting.Setting<?> setting) {
        if (setting == showLevel) names.clear();
        super.onSettingChanged(setting);
    }
}
