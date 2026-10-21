package dev.nexus.client.ui.vanilla;

import dev.nexus.client.Nexus;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import java.time.LocalTime;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

public final class TitleDecor {
    private static final Text LOGO = Text.literal("NEXUS").setStyle(Style.EMPTY.withBold(true));
    private static final int PARTICLES = 46;
    private static final float[] PX = new float[46];
    private static final float[] PY = new float[46];
    private static final float[] PS = new float[46];
    private static final float[] PV = new float[46];
    private static final float[] PP = new float[46];
    public final long openedAt = Util.getMeasuringTimeMs();
    public float logoTop;
    public float logoHeight;
    public float greetingY;
    public boolean showGreeting;
    @Nullable
    private Supplier<SkinTextures> skin;

    public void initSkin(MinecraftClient client) {
        try {
            this.skin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
        } catch (RuntimeException var3) {
            this.skin = null;
        }
    }

    public float intro(long delayMs, long durationMs) {
        float t = (float)(Util.getMeasuringTimeMs() - this.openedAt - delayMs) / (float)durationMs;
        t = Math.max(0.0F, Math.min(1.0F, t));
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    public void overlay(DrawContext ctx, int w, int h, boolean photos) {
        float time = (float)(Util.getMeasuringTimeMs() % 1000000L) / 1000.0F;
        if (photos) {
            int dim = Math.round(Nexus.settings().photoDim.getFloat() * 255.0F);
            if (dim > 0) Render2D.rect(ctx, 0.0F, 0.0F, w, h, dim << 24 | 0x07070B);
        } else {
            Render2D.gradientV(ctx, 0.0F, 0.0F, w, h, -1945630451, -804648176);
        }
        // clean title: no glow blobs, no floating particles (Lunaris-style sharp menu)

        Render2D.gradientV(ctx, 0.0F, 0.0F, w, h * 0.22F, 1879048192, 0);
        Render2D.gradientV(ctx, 0.0F, h * 0.78F, w, h * 0.22F, 0, -1879048192);
    }

    public void logo(DrawContext ctx, int w, @Nullable Text splash, boolean showSplash) {
        float t = this.intro(0L, 650L);
        if (!(t <= 0.0F)) {
            Render2D.pushAlpha(t);
            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            float cx = w / 2.0F;
            float big = this.logoHeight >= 80.0F ? 1.0F : 0.62F;
            float slide = (1.0F - t) * -6.0F;
            // wordmark: big NEXUS line with an accent glow
            float s = 4.4F * big;
            float textW = TextRender.width(LOGO) * s;
            float y0 = this.logoTop + slide;
            Render2D.shadow(ctx, cx - textW * 0.4F, y0 + 8.0F * big, textW * 0.8F, s * 3.0F, 10.0F, 24.0F * big,
                    ColorUtil.withAlpha(Theme.accent(), 110));
            TextRender.drawScaled(ctx, LOGO, cx - textW / 2.0F + s * 0.5F, y0 + s * 0.5F, s, 1879048192, false);
            TextRender.drawScaled(ctx, LOGO, cx - textW / 2.0F, y0, s, -1249805, false);
            // spaced CLIENT line in the accent colour
            String caption = spaced("CLIENT");
            float sub = 1.05F * big;
            float cw = TextRender.width(caption) * sub;
            float sy = y0 + 9.4F * s;
            TextRender.drawScaled(ctx, caption, cx - cw / 2.0F + 0.8F, sy + 0.8F, sub, 1879048192, false);
            TextRender.drawScaled(ctx, caption, cx - cw / 2.0F, sy, sub, Theme.accentBright(), false);
            if (showSplash && splash != null && this.logoHeight >= 80.0F) {
                float pulse = 1.0F + 0.04F * (float)Math.sin((float)Util.getMeasuringTimeMs() / 1000.0F * 6.283F);
                float ss = 0.82F * pulse;
                float sw = TextRender.width(splash) * ss;
                TextRender.drawScaled(ctx, splash, cx - sw / 2.0F, sy + 14.0F * big, ss, -137590, true);
            }

            m.popMatrix();
            Render2D.popAlpha();
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            if (i > 0) {
                b.append(' ');
            }

            b.append(s.charAt(i));
        }

        return b.toString();
    }

    public void greeting(DrawContext ctx, MinecraftClient client, int w) {
        if (this.showGreeting) {
            float t = this.intro(220L, 500L);
            if (!(t <= 0.0F)) {
                Render2D.pushAlpha(t);
                int hour = LocalTime.now().getHour();
                String key = hour < 5 ? "night" : (hour < 12 ? "morning" : (hour < 18 ? "day" : (hour < 23 ? "evening" : "night")));
                String name = client.getSession().getUsername();
                Text text = Text.translatable("nexus.title.greeting." + key, name);
                int tw = TextRender.width(text);
                float y = this.greetingY + (1.0F - t) * 4.0F;
                TextRender.draw(ctx, text, w / 2.0F - tw / 2.0F, y, -3355444, false);
                Render2D.popAlpha();
            }
        }
    }

    public void footer(DrawContext ctx, MinecraftClient client, int w, int h) {
        float t = this.intro(450L, 500L);
        if (!(t <= 0.0F)) {
            Render2D.pushAlpha(t);
            String line = "NEXUS Client " + Nexus.VERSION;
            TextRender.draw(ctx, line, 6.0F, h - 12.0F, -9605779, false);
            String copyright = "Copyright Mojang AB. Do not distribute!";
            int cw = TextRender.widthRaw(copyright);
            TextRender.drawRaw(ctx, copyright, w - 6.0F - cw, h - 12.0F, -9605779, false);
            Render2D.popAlpha();
        }
    }


    static {
        Random r = new Random(1313167445L);

        for (int i = 0; i < 46; i++) {
            PX[i] = r.nextFloat();
            PY[i] = r.nextFloat();
            PS[i] = 0.5F + r.nextFloat() * 1.3F;
            PV[i] = 0.006F + r.nextFloat() * 0.018F;
            PP[i] = r.nextFloat() * 6.283F;
        }
    }
}
