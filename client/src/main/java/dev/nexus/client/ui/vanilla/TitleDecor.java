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
        float gx = w * (0.3F + 0.05F * (float)Math.sin(time * 0.21F));
        float gy = h * (0.3F + 0.04F * (float)Math.cos(time * 0.17F));
        Render2D.shadow(ctx, gx - 30.0F, gy - 30.0F, 60.0F, 60.0F, 30.0F, Math.max(w, h) * 0.32F, ColorUtil.withAlpha(Theme.accent(), 30));
        float cx = w * (0.72F + 0.05F * (float)Math.cos(time * 0.19F));
        float cy = h * (0.68F + 0.04F * (float)Math.sin(time * 0.23F));
        Render2D.shadow(ctx, cx - 30.0F, cy - 30.0F, 60.0F, 60.0F, 30.0F, Math.max(w, h) * 0.28F, ColorUtil.withAlpha(-13058568, 20));

        for (int i = 0; i < 46; i++) {
            float y = PY[i] - time * PV[i];
            y -= (float)Math.floor(y);
            float x = PX[i] + 0.015F * (float)Math.sin(time * 0.6F + PP[i]);
            float twinkle = 0.45F + 0.55F * (float)Math.sin(time * 1.3F + PP[i]) * 0.5F + 0.275F;
            float edge = Math.min(1.0F, Math.min(y, 1.0F - y) * 6.0F);
            int base = i % 3 == 0 ? -13058568 : Theme.accentBright();
            int a = (int)(150.0F * twinkle * edge);
            if (a > 2) {
                Render2D.circle(ctx, x * w, y * h, PS[i], ColorUtil.withAlpha(base, a));
            }
        }

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
            float big = this.logoHeight >= 80.0F ? 1.0F : (this.logoHeight >= 56.0F ? 0.72F : 0.5F);
            float s = 3.2F * big;
            float textW = TextRender.width(LOGO) * s;
            float tile = 34.0F * big;
            float gap = 9.0F * big;
            float total = tile + gap + textW;
            float x0 = cx - total / 2.0F;
            float y0 = this.logoTop + (1.0F - t) * -6.0F + 4.0F * big;
            Render2D.shadow(ctx, x0 + 10.0F, y0 + tile * 0.3F, total - 20.0F, tile * 0.4F, 8.0F, 26.0F * big, ColorUtil.withAlpha(Theme.accent(), 64));
            Render2D.shadow(ctx, x0, y0 + 2.0F, tile, tile, 8.0F * big, 6.0F * big, Integer.MIN_VALUE);
            Render2D.roundedGradient(ctx, x0, y0, tile, tile, 8.0F * big, Theme.accent(), ColorUtil.lerp(Theme.accent(), -13058568, 0.6F), true);
            Render2D.roundedRect(ctx, x0 + 1.0F, y0 + 1.0F, tile - 2.0F, tile / 2.0F - 1.0F, 7.0F * big, 7.0F * big, 0.0F, 0.0F, 654311423);
            IconRender.drawCentered(ctx, Icon.LOGO, x0 + tile / 2.0F, y0 + tile / 2.0F, tile * 0.62F, Theme.onAccent());
            float ty = y0 + (tile - 8.0F * s) / 2.0F + 0.5F * s;
            TextRender.drawScaled(ctx, LOGO, x0 + tile + gap + s * 0.6F, ty + s * 0.6F, s, 1879048192, false);
            TextRender.drawScaled(ctx, LOGO, x0 + tile + gap, ty, s, -1249805, false);
            float sub = Math.max(0.6F, 0.78F * big + 0.12F);
            String caption = spaced("CLIENT") + "   ·   MINECRAFT " + SharedConstants.getGameVersion().name();
            float cw = TextRender.width(caption) * sub;
            float sy = y0 + tile + 9.0F * big;
            Render2D.gradientH(ctx, cx - cw / 2.0F - 34.0F, sy + 3.5F * sub, 28.0F, 1.0F, ColorUtil.withAlpha(Theme.accent(), 0), Theme.accent());
            Render2D.gradientH(ctx, cx + cw / 2.0F + 6.0F, sy + 3.5F * sub, 28.0F, 1.0F, Theme.accent(), ColorUtil.withAlpha(Theme.accent(), 0));
            TextRender.drawScaled(ctx, caption, cx - cw / 2.0F, sy, sub, Theme.accentBright(), false);
            if (showSplash && splash != null && this.logoHeight >= 56.0F) {
                float pulse = 1.0F + 0.04F * (float)Math.sin((float)Util.getMeasuringTimeMs() / 1000.0F * 6.283F);
                float ss = 0.82F * pulse;
                float sw = TextRender.width(splash) * ss;
                TextRender.drawScaled(ctx, splash, cx - sw / 2.0F, sy + 13.0F * big, ss, -137590, true);
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
                float head = 10.0F;
                float pad = 5.0F;
                float pw = head + 6.0F + tw + pad * 2.0F;
                float ph = 16.0F;
                float px = w / 2.0F - pw / 2.0F;
                float py = this.greetingY + (1.0F - t) * 4.0F;
                Render2D.roundedRect(ctx, px, py, pw, ph, ph / 2.0F, -1727000298);
                Render2D.roundedOutline(ctx, px, py, pw, ph, ph / 2.0F, 1.0F, 536870911);
                SkinTextures tex = null;
                if (this.skin != null) {
                    try {
                        tex = this.skin.get();
                    } catch (RuntimeException var19) {
                    }
                }

                if (tex == null) {
                    tex = DefaultSkinHelper.getSkinTextures(client.getGameProfile());
                }

                int hx = Math.round(px + pad);
                int hy = Math.round(py + (ph - head) / 2.0F);
                Render2D.roundedRect(ctx, hx - 1, hy - 1, head + 2.0F, head + 2.0F, 2.0F, Theme.accent());
                PlayerSkinDrawer.draw(ctx, tex, hx, hy, (int)head);
                TextRender.draw(ctx, text, px + pad + head + 6.0F, py + (ph - 8.0F) / 2.0F + 0.5F, -1249805, false);
                Render2D.popAlpha();
            }
        }
    }

    public void footer(DrawContext ctx, MinecraftClient client, int h) {
        float t = this.intro(450L, 500L);
        if (!(t <= 0.0F)) {
            Render2D.pushAlpha(t);
            String vanilla = "Minecraft " + SharedConstants.getGameVersion().name();
            if (client.isDemo()) {
                vanilla = vanilla + " Demo";
            } else if (!"release".equalsIgnoreCase(client.getVersionType())) {
                vanilla = vanilla + "/" + client.getVersionType();
            }

            if (MinecraftClient.getModStatus().isModded()) {
                vanilla = vanilla + I18n.translate("menu.modded");
            }

            float x = 6.0F;
            float y = h - 12;
            Render2D.circle(ctx, x + 2.5F, y - 7.5F, 2.5F, Theme.accent());
            TextRender.draw(ctx, "NEXUS Client " + Nexus.VERSION, x + 9.0F, y - 11.0F, Theme.accentBright(), false);
            TextRender.draw(ctx, vanilla, x, y, -6644563, false);
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
