package dev.nexus.client.module.impl.hud;

import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.MultiSelectSetting;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Shows worn armour (and optionally held items) with durability. */
public final class ArmorHudModule extends HudModule {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final ModeSetting layout = add(new ModeSetting("layout", "Layout", "Arrangement of the items",
            "Vertical", "Vertical", "Horizontal"));
    private static final String[] SLOT_NAMES = {"Helmet", "Chestplate", "Leggings", "Boots", "Main hand", "Off hand"};
    private final MultiSelectSetting slots = add(new MultiSelectSetting("slots", "Slots", "Which equipment slots are shown",
            java.util.List.of(SLOT_NAMES), SLOT_NAMES));
    private final ModeSetting durability = add(new ModeSetting("durability", "Durability", "How durability is shown",
            "Percent", "Percent", "Value", "Bar", "Hidden"));
    private final BooleanSetting colorize = add(new BooleanSetting("colorize", "Colored durability",
            "Colour durability from green to red", true));

    private final ItemStack[] visible = new ItemStack[SLOTS.length];
    // per-slot label cache
    private final Item[] cacheItem = new Item[SLOTS.length];
    private final int[] cacheDamage = new int[SLOTS.length];
    private final String[] cacheMode = new String[SLOTS.length];
    private final String[] cacheLabel = new String[SLOTS.length];
    private final int[] cacheColor = new int[SLOTS.length];
    private ItemStack[] preview;

    public ArmorHudModule() {
        super("armor_hud", "Armor HUD", "Displays your armour and held items with remaining durability.",
                false, START, CENTER, 4, 0);
    }

    private int collect(boolean editor) {
        int n = 0;
        if (mc.player != null) {
            for (int i = 0; i < SLOTS.length; i++) {
                if (!slots.isSelected(SLOT_NAMES[i])) continue;
                ItemStack s = mc.player.getEquippedStack(SLOTS[i]);
                if (!s.isEmpty()) visible[n++] = s;
            }
        }
        if (n == 0 && editor) {
            if (preview == null) {
                preview = new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                        new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.DIAMOND_SWORD),
                        new ItemStack(Items.SHIELD)};
                preview[1].setDamage(preview[1].getMaxDamage() / 3);
                preview[2].setDamage(preview[2].getMaxDamage() * 4 / 5);
            }
            for (int i = 0; i < preview.length; i++) if (slots.isSelected(SLOT_NAMES[i])) visible[n++] = preview[i];
        }
        return n;
    }

    private String label(int i, ItemStack stack) {
        String mode = durability.get();
        if (!stack.isDamageable() || mode.equals("Bar") || mode.equals("Hidden")) return "";
        if (cacheItem[i] != stack.getItem() || cacheDamage[i] != stack.getDamage() || !mode.equals(cacheMode[i])) {
            int max = stack.getMaxDamage(), remaining = max - stack.getDamage();
            float ratio = max > 0 ? remaining / (float) max : 0;
            cacheLabel[i] = mode.equals("Value") ? Integer.toString(remaining) : Math.round(ratio * 100) + "%";
            cacheColor[i] = ratio > 0.5f ? ColorUtil.lerp(0xFFFFE066, 0xFF5DF08A, (ratio - 0.5f) * 2)
                    : ColorUtil.lerp(0xFFFF5A5A, 0xFFFFE066, ratio * 2);
            cacheItem[i] = stack.getItem();
            cacheDamage[i] = stack.getDamage();
            cacheMode[i] = mode;
        }
        return cacheLabel[i];
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        int n = collect(editor);
        if (n == 0) return false;
        boolean vertical = layout.is("Vertical");
        boolean right = isRightAligned();
        int pad = 4;
        int labelWidth = 0;
        for (int i = 0; i < n; i++) labelWidth = Math.max(labelWidth, TextRender.width(label(i, visible[i])));
        boolean hasLabels = labelWidth > 0;

        if (vertical) {
            int rowH = 18;
            int w = pad * 2 + 16 + (hasLabels ? 4 + labelWidth : 0);
            int h = pad * 2 + n * rowH - 2;
            setSize(w, h);
            HudStyle.panel(ctx, this, w, h);
            for (int i = 0; i < n; i++) {
                int y = pad + i * rowH;
                int itemX = right ? w - pad - 16 : pad;
                drawStack(ctx, visible[i], itemX, y);
                String l = label(i, visible[i]);
                if (!l.isEmpty()) {
                    int color = colorize.isOn() ? cacheColor[i] : HudStyle.VALUE;
                    float tx = right ? itemX - 4 - TextRender.width(l) : itemX + 20;
                    HudStyle.text(ctx, l, tx, y + 4, color);
                }
            }
        } else {
            int colW = Math.max(18, labelWidth + 2);
            int w = pad * 2 + n * colW - (colW - 16);
            int h = pad * 2 + 16 + (hasLabels ? 10 : 0);
            setSize(w, h);
            HudStyle.panel(ctx, this, w, h);
            for (int i = 0; i < n; i++) {
                int x = pad + i * colW;
                drawStack(ctx, visible[i], x, pad);
                String l = label(i, visible[i]);
                if (!l.isEmpty()) {
                    int color = colorize.isOn() ? cacheColor[i] : HudStyle.VALUE;
                    HudStyle.text(ctx, l, x + 8 - TextRender.width(l) / 2f, pad + 18, color);
                }
            }
        }
        return true;
    }

    private void drawStack(DrawContext ctx, ItemStack stack, int x, int y) {
        ctx.drawItem(stack, x, y);
        // vanilla overlay = durability bar + stack count
        if (durability.is("Bar") || (!stack.isDamageable() && stack.getCount() > 1)) {
            ctx.drawStackOverlay(mc.textRenderer, stack, x, y);
        }
    }
}
