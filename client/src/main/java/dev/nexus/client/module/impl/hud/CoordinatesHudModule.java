package dev.nexus.client.module.impl.hud;

import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Locale;
import java.util.Optional;

/** Player coordinates with configurable format; strings are rebuilt only when values change. */
public final class CoordinatesHudModule extends HudModule {
    private static final int MAX_ROWS = 6;

    private final ModeSetting format = add(new ModeSetting("format", "Format", "Layout of the coordinates",
            "Inline", "Inline", "Stacked", "Custom"));
    private final StringSetting template = add(new StringSetting("template", "Template",
            "Placeholders: {x} {y} {z} {dir} {biome}", "X {x}  Y {y}  Z {z}").maxLength(64)
            .placeholder("X {x} Y {y} Z {z}")).visibleWhen(() -> format.is("Custom"));
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals",
            "Digits after the decimal point", 0, 0, 2, 1));
    private final BooleanSetting direction = add(new BooleanSetting("direction", "Facing",
            "Show the direction you are facing", true));
    private final BooleanSetting nether = add(new BooleanSetting("nether", "Nether coordinates",
            "Show the matching Overworld/Nether position", true));
    private final BooleanSetting biome = add(new BooleanSetting("biome", "Biome", "Show the current biome", false));

    private final String[] labels = new String[MAX_ROWS];
    private final String[] values = new String[MAX_ROWS];
    private int rows;
    private double lastX = Double.NaN, lastY, lastZ;
    private Direction lastFacing;
    private RegistryKey<World> lastDim;
    private long lastBlock = Long.MIN_VALUE;
    private String biomeName = "";
    private int settingsStamp = -1;
    private int changeCounter;

    public CoordinatesHudModule() {
        super("coordinates_hud", "Coordinates HUD", "Shows your position, facing, biome and nether coordinates.",
                true, START, START, 4, 4);
    }

    @Override
    public void onSettingChanged(dev.nexus.client.setting.Setting<?> setting) {
        changeCounter++;
        super.onSettingChanged(setting);
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return false;
        update(p);
        float pad = HudStyle.PAD_X;
        float labelCol = 0, valueCol = 0;
        for (int i = 0; i < rows; i++) {
            if (labels[i] != null) labelCol = Math.max(labelCol, TextRender.width(labels[i]));
            valueCol = Math.max(valueCol, TextRender.width(values[i]));
        }
        float gap = labelCol > 0 ? 5 : 0;
        float rowH = 10f;
        float w = pad * 2 + labelCol + gap + valueCol;
        float h = HudStyle.PAD_Y * 2 + rows * rowH - 1;
        setSize(w, h);
        HudStyle.panel(ctx, this, w, h);
        for (int i = 0; i < rows; i++) {
            float y = HudStyle.PAD_Y + i * rowH + 1;
            if (labels[i] != null) HudStyle.text(ctx, labels[i], pad, y, HudStyle.LABEL);
            HudStyle.text(ctx, values[i], pad + labelCol + gap, y, HudStyle.VALUE);
        }
        return true;
    }

    private void update(ClientPlayerEntity p) {
        int dec = decimals.getInt();
        double f = Math.pow(10, dec);
        double x = Math.floor(p.getX() * f) / f, y = Math.floor(p.getY() * f) / f, z = Math.floor(p.getZ() * f) / f;
        Direction facing = p.getHorizontalFacing();
        RegistryKey<World> dim = mc.world.getRegistryKey();
        BlockPos block = p.getBlockPos();
        long blockKey = block.asLong();
        if (biome.isOn() && blockKey != lastBlock) {
            Optional<RegistryKey<Biome>> key = mc.world.getBiome(block).getKey();
            biomeName = key.map(k -> pretty(k.getValue().getPath())).orElse("Unknown");
        }
        boolean changed = x != lastX || y != lastY || z != lastZ || facing != lastFacing || dim != lastDim
                || settingsStamp != changeCounter || (biome.isOn() && blockKey != lastBlock);
        lastBlock = blockKey;
        if (!changed) return;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastFacing = facing;
        lastDim = dim;
        settingsStamp = changeCounter;

        String fx = num(x, dec), fy = num(y, dec), fz = num(z, dec);
        String dir = pretty(facing.asString()) + " (" + axis(facing) + ")";
        rows = 0;
        switch (format.get()) {
            case "Stacked" -> {
                row("X", fx);
                row("Y", fy);
                row("Z", fz);
            }
            case "Custom" -> row(null, template.get().replace("{x}", fx).replace("{y}", fy).replace("{z}", fz)
                    .replace("{dir}", dir).replace("{biome}", biomeName));
            default -> row("XYZ", fx + "  " + fy + "  " + fz);
        }
        if (direction.isOn() && !format.is("Custom")) row("Facing", dir);
        if (nether.isOn()) {
            if (dim == World.NETHER) row("Overworld", num(x * 8, 0) + "  " + num(z * 8, 0));
            else if (dim == World.OVERWORLD) row("Nether", num(x / 8, 0) + "  " + num(z / 8, 0));
        }
        if (biome.isOn() && !format.is("Custom")) row("Biome", biomeName);
    }

    private void row(String label, String value) {
        if (rows >= MAX_ROWS) return;
        labels[rows] = label;
        values[rows] = value;
        rows++;
    }

    private static String num(double v, int decimals) {
        if (decimals == 0) return Long.toString((long) Math.floor(v));
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    private static String axis(Direction d) {
        return switch (d) {
            case NORTH -> "-Z";
            case SOUTH -> "+Z";
            case WEST -> "-X";
            case EAST -> "+X";
            default -> "";
        };
    }

    static String pretty(String id) {
        StringBuilder sb = new StringBuilder(id.length());
        boolean upper = true;
        for (char c : id.toCharArray()) {
            if (c == '_' || c == '/') {
                sb.append(' ');
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }
}
