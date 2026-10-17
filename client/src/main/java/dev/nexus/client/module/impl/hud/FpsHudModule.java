package dev.nexus.client.module.impl.hud;

import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Frames per second (vanilla counter) and an optional measured frame time. */
public final class FpsHudModule extends HudModule {
    private final BooleanSetting label = add(new BooleanSetting("label", "Label", "Show the \"FPS\" label", true));
    private final BooleanSetting frameTime = add(new BooleanSetting("frame_time", "Frame time",
            "Show the average frame time in milliseconds", false));
    private final BooleanSetting colorize = add(new BooleanSetting("colorize", "Color by value",
            "Green above 60, yellow above 30, red below", true));

    private long lastFrame = -1;
    private double avgFrameMs = 16.6;
    private int cachedFps = -1, cachedMsTenths = -1;
    private String fpsText = "", msText = "";

    public FpsHudModule() {
        super("fps_counter", "FPS Counter", "Shows your current frame rate and optional frame time.",
                true, START, START, 4, 50);
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        long now = System.nanoTime();
        if (lastFrame > 0) {
            double ms = (now - lastFrame) / 1_000_000.0;
            if (ms < 1000) avgFrameMs += (ms - avgFrameMs) * 0.05;
        }
        lastFrame = now;
        int fps = mc.getCurrentFps();
        if (fps != cachedFps) {
            cachedFps = fps;
            fpsText = Integer.toString(fps);
        }
        int tenths = (int) Math.round(avgFrameMs * 10);
        if (tenths != cachedMsTenths) {
            cachedMsTenths = tenths;
            msText = String.format(Locale.ROOT, "%.1f ms", tenths / 10.0);
        }
        float pad = HudStyle.PAD_X;
        float w = pad * 2 + TextRender.width(fpsText);
        if (label.isOn()) w += 3 + TextRender.width("FPS");
        if (frameTime.isOn()) w += 6 + TextRender.width(msText);
        float h = HudStyle.PAD_Y * 2 + 8;
        setSize(w, h);
        HudStyle.panel(ctx, this, w, h);
        int color = !colorize.isOn() ? HudStyle.VALUE : fps >= 60 ? 0xFF5DF08A : fps >= 30 ? 0xFFFFE066 : 0xFFFF5A5A;
        float x = pad, y = HudStyle.PAD_Y + 0.5f;
        HudStyle.text(ctx, fpsText, x, y, color);
        x += TextRender.width(fpsText);
        if (label.isOn()) {
            HudStyle.text(ctx, "FPS", x + 3, y, HudStyle.LABEL);
            x += 3 + TextRender.width("FPS");
        }
        if (frameTime.isOn()) HudStyle.text(ctx, msText, x + 6, y, HudStyle.LABEL);
        return true;
    }
}
