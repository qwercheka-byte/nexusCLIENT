package dev.nexus.client.ui.vanilla;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Lunaris-style main menu on a fixed 1920x1080 design grid, scaled down to the window:
 * sharp fullscreen photo (no glow blobs), brand top-left, avatar pill top-right,
 * bold greeting, three 300x400 photo cards with a frosted icon square, dynamic subtitles
 * (last world + time ago / live FunTime online / profile name), a blue "Play" button
 * inside the focused card, and three pill buttons at the bottom.
 */
public final class SkyTitle {
    public static final SkyTitle INSTANCE = new SkyTitle();

    private static final int BLUE = 0xFF8DC5FD;
    private static final int BLUE_HOVER = 0xFFAAD4FF;
    private static final int NAVY = 0xFF040E2A;
    private static final int WHITE_90 = 0xFFFCFEFE;
    private static final int DIM_TXT = 0xFFC4D4DF;
    private static final int CARD_ICON_BG = 0x24FFFFFF;
    private static final int BTN_FILL = 0xE80F1316;
    private static final int BTN_FILL_HOVER = 0xF01E2328;
    private static final int QUIT_FILL = 0xFF955F5F;
    private static final int QUIT_FILL_HOVER = 0xFFB06C6C;
    private static final int QUIT_TEXT = 0xFFFFF4F3;

    private ClickableWidget single, multi, options, quit;
    private Runnable openNexus;

    private int w, h;
    private long openStamp;

    // hover animations (0..1), eased toward the hovered card / hovered play button
    private final float[] cardT = {0.0F, 0.0F, 0.0F};
    private final float[] playT = {0.0F, 0.0F, 0.0F};
    private final float[] barT = {0.0F, 0.0F, 0.0F};
    private long lastFrame;

    // captured rects for hit tests: [x, y, w, h, id] — cards 0..2, bar 10..12
    private final java.util.List<float[]> hitRects = new java.util.ArrayList<>();

    // live data for the dynamic subtitles
    private LevelSummary lastWorld;
    private long lastWorldSince;
    private final MultiplayerServerListPinger ftPinger = new MultiplayerServerListPinger();
    private final ServerInfo funTime = new ServerInfo("FunTime", "mc.funtime.su", ServerInfo.ServerType.OTHER);

    private SkyTitle() {}

    public void bind(ClickableWidget single, ClickableWidget multi, ClickableWidget options,
                     ClickableWidget quit, Runnable openNexus) {
        this.single = single;
        this.multi = multi;
        this.options = options;
        this.quit = quit;
        this.openNexus = openNexus;
    }

    public void open() {
        openStamp = Util.getMeasuringTimeMs();
        lastFrame = openStamp;
        lastWorld = null;
        // fetch the most recently played singleplayer world (for the first card's subtitle)
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            client.getLevelStorage().loadSummaries(client.getLevelStorage().getLevelList()).thenAccept(levels ->
                    lastWorld = levels.stream().max(Comparator.comparingLong(LevelSummary::getLastPlayed)).orElse(null));
        } catch (LevelStorageException ignored) { }
        // FunTime live online counter for the multiplayer card (one-shot ping, cancelled after it lands)
        CompletableFuture.runAsync(() -> {
            try {
                ftPinger.add(funTime, () -> {}, () -> {}, NetworkingBackend.remote(client.options.shouldUseNativeTransport()));
            } catch (Exception ignored) { }
        }, Util.getDownloadWorkerExecutor());
    }

    /** kept for API compatibility with the title mixin (layout is computed per frame now) */
    public void layout(int w, int h) { }

    // ------------------------------------------------------------------------------------------------ frame math

    private float s() { // exact fit: the 1920x1080 design canvas maps onto the window edge-to-edge
        return Math.max(0.125F, Math.min(this.w / 1920.0F, this.h / 1080.0F));
    }
    private float x(float d) { return this.w / 2.0F + (d - 960.0F) * s(); }
    private float y(float d) { return this.h / 2.0F + (d - 540.0F) * s(); }
    /** font scale factor for a design-space pixel size (base glyph is 8 px tall) */
    private float fs(float px) { return px * s() / 8.0F; }

    private float intro(long delayMs, long durationMs) {
        float t = (float)(Util.getMeasuringTimeMs() - openStamp - delayMs) / (float)durationMs;
        t = Math.max(0.0F, Math.min(1.0F, t));
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    /** eases target values toward their hover state with the frame delta (like LunarisMenu.animate) */
    private void step(float[] arr, int i, boolean on, long now) {
        float dt = Math.min(0.05F, Math.max(0.0F, (now - lastFrame < 0 ? 0 : now - lastFrame) / 220.0F));
        arr[i] += ((on ? 1.0F : 0.0F) - arr[i]) * dt * 2.6F;
        if (arr[i] > 0.999F) arr[i] = 1.0F;
        else if (arr[i] < 0.001F) arr[i] = 0.0F;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    public void draw(DrawContext ctx, MinecraftClient client, int logicalW, int logicalH, int mouseX, int mouseY) {
        this.w = logicalW;
        this.h = logicalH;
        long now = Util.getMeasuringTimeMs();
        ftPinger.tick();
        hitRects.clear();

        brand(ctx, intro(60L, 480L));
        pill(ctx, client, intro(120L, 480L));
        greeting(ctx, client);

        // eased growth: the hovered card expands, the rest shrink back
        for (int i = 0; i < 3; i++) {
            step(cardT, i, inCard(i, mouseX, mouseY), now);
            card(ctx, i, cardT[i], client, mouseX, mouseY, intro(280L + i * 70L, 520L), now);
        }
        bar(ctx, mouseX, mouseY, now);
        this.lastFrame = now;
    }

    private void brand(DrawContext ctx, float t) {
        if (t <= 0.0F) return;
        Render2D.pushAlpha(t);
        float lift = (1.0F - t) * 12.0F * s();
        TextRender.drawScaled(ctx, Text.literal("NEXUS").setStyle(Style.EMPTY.withBold(true)),
                x(54.0F), y(41.0F) + lift, fs(17.0F), -1, false);
        Render2D.popAlpha();
    }

    /** avatar + nickname pill, top-right; Lunaris pills: dark body + hairline outline + chevron */
    private void pill(DrawContext ctx, MinecraftClient client, float t) {
        if (t <= 0.0F) return;
        Render2D.pushAlpha(t);
        float lift = (1.0F - t) * 12.0F * s();
        String name = client.getSession().getUsername();
        float nw = TextRender.widthRaw(name) * fs(13.2F);
        float size = 26.0F * s();
        float pw = nw + size + 30.0F * s();
        float px = this.w - 56.0F * s() - pw + 24.0F * s();
        float py = 40.0F * s() + lift;
        float ph = 37.0F * s();
        float r = 10.0F * s();
        Render2D.roundedRect(ctx, px, py, pw, ph, r, 0xE10A0E10);
        Render2D.roundedOutline(ctx, px, py, pw, ph, r, 1.0F, 0x16FFFFFF);
        boolean face = false;
        try {
            java.util.function.Supplier<SkinTextures> skin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
            SkinTextures tex = skin != null ? skin.get() : null;
            if (tex != null) {
                PlayerSkinDrawer.draw(ctx, tex, Math.round(px + 6.0F * s()), Math.round(py + (ph - size) / 2.0F), Math.round(size));
                face = true;
            }
        } catch (Throwable ignored) { }
        if (!face) {
            Render2D.roundedRect(ctx, px + 6.0F * s(), py + (ph - size) / 2.0F, size, size, 5.0F * s(),
                    dev.nexus.client.ui.theme.Theme.accent());
            TextRender.drawScaled(ctx, Text.literal(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase()),
                    px + 6.0F * s() + size * 0.28F, py + (ph - size) / 2.0F + size * 0.22F, fs(13.2F), -1, false);
        }
        TextRender.drawScaled(ctx, Text.literal(name), px + 6.0F * s() + size + 6.0F * s(),
                py + ph / 2.0F - 4.0F * fs(13.2F), fs(13.2F), 0xFFF7FBFC, false);
        IconRender.draw(ctx, Icon.CHEVRON_DOWN, px + pw - 17.0F * s(), py + ph / 2.0F - 3.5F * s(), 7.0F * s(), 0xFF8C9194);
        Render2D.popAlpha();
    }

    private void greeting(DrawContext ctx, MinecraftClient client) {
        float t = intro(220L, 520L);
        if (t <= 0.0F) return;
        Render2D.pushAlpha(t);
        float lift = (1.0F - t) * 12.0F * s();
        String name = client.getSession().getUsername();
        String greet = I18n.translate("nexus.title.sky.greeting", name);
        int gw = TextRender.widthRaw(greet);
        TextRender.drawScaled(ctx, Text.literal(greet).setStyle(Style.EMPTY.withBold(true)),
                this.w / 2.0F - gw * fs(30.9F) / 2.0F, y(234.0F) + lift, fs(30.9F), WHITE_90, false);
        String hint = I18n.translate("nexus.title.sky.hint");
        int hw = TextRender.widthRaw(hint);
        TextRender.drawScaled(ctx, Text.literal(hint),
                this.w / 2.0F - hw * fs(14.2F) / 2.0F, y(280.0F) + lift, fs(14.2F), 0xFFC4D4DF, false);
        Render2D.popAlpha();
    }

    // ------------------------------------------------------------------------------------------------ cards

    private static final float[] CARD_CX = {630.0F, 960.0F, 1290.0F};

    private final int[] dims = new int[2];

    private void card(DrawContext ctx, int i, float t, MinecraftClient client, int mouseX, int mouseY, float a, long now) {
        if (a <= 0.0F) return;
        Render2D.pushAlpha(a);
        float lift = (1.0F - a) * 12.0F * s();
        float w = 300.0F + 20.0F * t;
        float h = 400.0F + 28.0F * t;
        float left = x(CARD_CX[i]) - w * s() / 2.0F;
        float top = y(556.0F) - h * s() / 2.0F + lift;
        float sw = w * s(), sh = h * s();
        float r = 20.0F * (w / 300.0F) * s();
        hitRects.add(new float[]{left, top, sw, sh, i});

        // cover-fit photo clipped to the rounded silhouette (banded scissor like before)
        Render2D.rect(ctx, left, top, sw, sh, 0xFF0D121C);
        Identifier ph = MenuBackground.get().cardPhoto(i, dims);
        int bands = 16;
        for (int b = 0; b < bands; b++) {
            float y0 = top + sh * b / bands;
            float y1 = top + sh * (b + 1) / bands;
            float inset = cornerInset(y0 + (y1 - y0) / 2.0F - top, sh, r);
            ctx.enableScissor(Math.round(left + inset), Math.round(y0), Math.round(left + sw - inset), Math.round(y1));
            if (ph != null && dims[0] > 0 && dims[1] > 0) {
                float sc = Math.max(sw / (float) dims[0], sh / (float) dims[1]);
                float dw = dims[0] * sc, dh = dims[1] * sc;
                org.joml.Matrix3x2fStack m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(left + sw / 2.0F - dw / 2.0F, top + sh / 2.0F - dh / 2.0F);
                m.scale(sc, sc);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ph, 0, 0, 0.0F, 0.0F,
                        dims[0], dims[1], dims[0], dims[1], dims[0], dims[1], -1);
                m.popMatrix();
            } else {
                Render2D.rect(ctx, left, y0, sw, y1 - y0, 0xFF161C28);
            }
            ctx.disableScissor();
        }
        // soft bottom gradient so the texts read over any photo; stronger while not hovered
        int shadeA = Math.round(235.0F * (1.0F - 0.3F * t));
        Render2D.gradientV(ctx, left, top + sh - 150.0F * s(), sw, 150.0F * s(), 0, shadeA << 24 | 0x06080A);
        Render2D.roundedOutline(ctx, left - 0.5F, top - 0.5F, sw + 1.0F, sh + 1.0F, r + 0.5F, 1.5F, 0x2AFFFFFF);

        float dy52 = 58.0F * t * s();
        // frosted icon square: white glass -> blue on the active card
        float sq = 40.0F * s();
        float sqX = left + 20.0F * s();
        float sqY = top + sh - 122.0F * s() - dy52 - sq / 2.0F;
        Render2D.roundedRect(ctx, sqX, sqY, sq, sq, 10.0F * s(), lerp(CARD_ICON_BG, BLUE & 0xFFFFFF00 | 0xFF, t));
        Icon icon = i == 0 ? Icon.HOME : i == 1 ? Icon.GRID : Icon.PROFILES;
        IconRender.drawCentered(ctx, icon, sqX + sq / 2.0F, sqY + sq / 2.0F, sq * 0.56F, lerp(0xFFFCFDFD, NAVY, t));

        // title + dynamic subtitle
        String cap = I18n.translate("nexus.title.sky.card" + (i + 1));
        String sub = subtitle(i, client);
        TextRender.drawScaled(ctx, Text.literal(cap).setStyle(Style.EMPTY.withBold(true)),
                left + 20.0F * s(), top + sh - 45.0F * s() - dy52 - 11.5F * fs(22.6F), fs(22.6F), WHITE_90, false);
        TextRender.drawScaled(ctx, Text.literal(sub), left + 19.5F * s(),
                top + sh - 23.0F * s() - dy52 - 6.0F * fs(13.0F), fs(13.0F), 0xFFD0D2D6, false);

        // blue play button fades in on the active card
        if (t > 0.01F) {
            float bh = 40.0F * s();
            float bw = sw - 40.0F * s();
            boolean phov = mouseX >= left + 20.0F * s() && mouseX <= left + 20.0F * s() + bw
                    && mouseY >= top + sh - 60.0F * s() && mouseY <= top + sh - 60.0F * s() + bh;
            step(playT, i, phov, now);
            Render2D.pushAlpha(t);
            int fill = lerp(BLUE, BLUE_HOVER, playT[i]);
            Render2D.roundedRect(ctx, left + 20.0F * s(), top + sh - 60.0F * s(), bw, bh, 10.0F * s(), fill);
            String btn = I18n.translate("nexus.title.sky.play");
            int btw = TextRender.widthRaw(btn.replace(" →", ""));
            String label = btn.replace(" →", "");
            float cx = left + sw / 2.0F;
            TextRender.drawScaled(ctx, Text.literal(label).setStyle(Style.EMPTY.withBold(true)),
                    cx - btw * fs(14.5F) / 2.0F - 3.0F * s(), top + sh - 60.0F * s() + bh / 2.0F - 4.0F * fs(14.5F), fs(14.5F), NAVY, false);
            float triX = cx + btw * fs(14.5F) / 2.0F + 4.0F * s() + 2.0F * playT[i];
            float triY = top + sh - 60.0F * s() + bh / 2.0F;
            float ts = 4.5F * s();
            Render2D.line(ctx, triX, triY - ts, triX, triY + ts, 1.2F * s(), NAVY);
            Render2D.line(ctx, triX, triY - ts, triX + ts * 1.4F, triY, 1.2F * s(), NAVY);
            Render2D.line(ctx, triX, triY + ts, triX + ts * 1.4F, triY, 1.2F * s(), NAVY);
            Render2D.popAlpha();
        }
        Render2D.popAlpha();
    }

    private String subtitle(int i, MinecraftClient client) {
        if (i == 0) {
            LevelSummary wsum = lastWorld;
            if (wsum == null) return I18n.translate("nexus.title.sky.newworld");
            if (lastWorldSince != wsum.getLastPlayed()) lastWorldSince = wsum.getLastPlayed();
            return wsum.getDisplayName() + " · " + ago(lastWorldSince);
        }
        if (i == 1) {
            if (funTime.players == null) return funTime.name;
            String n = String.format(Locale.ROOT, "%,d", funTime.players.online()).replace(',', ' ');
            return I18n.translate("nexus.title.sky.online", funTime.name, n);
        }
        return client.getSession().getUsername();
    }

    private static String ago(long time) {
        long minutes = (System.currentTimeMillis() - time) / 60000L;
        if (minutes < 1L) return I18n.translate("nexus.title.sky.ago.just");
        if (minutes < 60L) return I18n.translate("nexus.title.sky.ago.min", minutes);
        if (minutes < 1440L) return I18n.translate("nexus.title.sky.ago.hr", minutes / 60L);
        return I18n.translate("nexus.title.sky.ago.day", minutes / 1440L);
    }

    /** horizontal inset of the rounded silhouette at local height ly */
    private static float cornerInset(float ly, float h, float r) {
        float d;
        if (ly < r) d = r - ly;
        else if (ly > h - r) d = ly - (h - r);
        else return 0.0F;
        return r - (float)Math.sqrt(Math.max(0.0F, r * r - d * d));
    }

    private static int lerp(int a, int b, float t) {
        if (t <= 0.0F) return a;
        if (t >= 1.0F) return b;
        int aa = a >>> 24, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    // ------------------------------------------------------------------------------------------------ bottom bar

    private void bar(DrawContext ctx, int mouseX, int mouseY, long now) {
        float a = intro(540L, 420L);
        if (a <= 0.0F) return;
        Render2D.pushAlpha(a);
        float lift = (1.0F - a) * 12.0F * s();
        float bh = 42.0F * s();
        float r = 12.0F * s();
        float y = y(806.0F) + lift;
        String[] labels = {I18n.translate("nexus.title.sky.settings"), I18n.translate("nexus.title.sky.mods"),
                I18n.translate("nexus.title.sky.quit")};
        Icon[] icons = {Icon.SETTINGS, Icon.REFRESH, Icon.POWER};
        float[] widths = new float[3];
        float total = 24.0F * s() * 2.0F;
        for (int i = 0; i < 3; i++) {
            widths[i] = (TextRender.widthRaw(labels[i]) * fs(14.0F)) + 52.0F * s();
            total += widths[i];
        }
        float x = this.w / 2.0F - total / 2.0F;
        for (int i = 0; i < 3; i++) {
            boolean hov = mouseX >= x && mouseX <= x + widths[i] && mouseY >= y && mouseY <= y + bh;
            step(barT, i, hov, now);
            boolean isQuit = i == 2;
            int fill = isQuit ? QUIT_FILL : lerp(BTN_FILL, BTN_FILL_HOVER, barT[i]);
            if (isQuit) fill = lerp(QUIT_FILL, QUIT_FILL_HOVER, barT[i]);
            Render2D.roundedRect(ctx, x, y, widths[i], bh, r, fill);
            int txtCol = isQuit ? QUIT_TEXT : 0xFFF8FBFD;
            float szi = 15.0F * s();
            IconRender.draw(ctx, icons[i], x + 14.0F * s(), y + bh / 2.0F - szi / 2.0F, szi, txtCol);
            TextRender.drawScaled(ctx, Text.literal(labels[i]), x + 14.0F * s() + szi + 9.0F * s(),
                    y + bh / 2.0F - 4.0F * fs(14.0F), fs(14.0F), txtCol, false);
            hitRects.add(new float[]{x, y, widths[i], bh, 10 + i});
            x += widths[i] + 24.0F * s();
        }
        Render2D.popAlpha();
    }

    // ------------------------------------------------------------------------------------------------ clicks

    private boolean inCard(int i, int mouseX, int mouseY) {
        float w = 300.0F, h = 400.0F;
        float left = x(CARD_CX[i]) - w * s() / 2.0F;
        float top = y(556.0F) - h * s() / 2.0F;
        return mouseX >= left && mouseX <= left + w * s() && mouseY >= top && mouseY <= top + h * s();
    }

    public boolean click(net.minecraft.client.gui.Click input) {
        int mx = (int) input.x(), my = (int) input.y();
        for (float[] rr : hitRects) {
            if (hit(mx, my, rr[0], rr[1], rr[2], rr[3])) {
                switch ((int) rr[4]) {
                    case 0 -> press(single, input);
                    case 1 -> press(multi, input);
                    case 2 -> { if (openNexus != null) openNexus.run(); }
                    case 10 -> press(options, input);
                    case 11 -> { if (openNexus != null) openNexus.run(); }
                    default -> press(quit, input);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return w > 0 && mx >= x && my >= y && mx <= x + w && my <= y + h;
    }

    private static void press(ClickableWidget w, net.minecraft.client.gui.Click input) {
        if (w == null) return;
        w.playDownSound(MinecraftClient.getInstance().getSoundManager());
        if (w instanceof ButtonWidget b) b.onPress(input);
    }
}
