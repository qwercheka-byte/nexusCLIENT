package dev.nexus.client.ui.vanilla;

import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Sky-DLC-style title screen: full-photo background, centered greeting, three photo cards
 * (singleplayer / multiplayer / NEXUS menu) and a small pill bar at the bottom (settings / quit).
 * The vanilla widgets are moved off-screen; this class owns the hit-testing and dispatch.
 */
public final class SkyTitle {
    public static final SkyTitle INSTANCE = new SkyTitle();

    // captured vanilla widgets; nulls mean "action unavailable"
    private ClickableWidget single, multi, options, quit;
    private Runnable openNexus;
    private ButtonWidget primary;

    // geometry, recomputed on every resize
    private float cx1, cx2, cx3, cy, cw, ch;
    private float bx1, bx2, by, bw1, bw2, bh;
    private float greetY;
    private int animSeed;
    private long openStamp;

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
        openStamp = net.minecraft.util.Util.getMeasuringTimeMs();
        animSeed++;
    }

    public void layout(int w, int h) {
        int safeW = Math.max(260, w);
        int safeH = Math.max(180, h);
        float gap = Math.max(10.0F, safeW * 0.021F);
        ch = Math.min(safeH * 0.48F, 118.0F);
        cw = ch * 0.74F;
        float total = cw * 3.0F + gap * 2.0F;
        float start = safeW / 2.0F - total / 2.0F;
        cy = safeH * 0.40F;
        cx1 = start;
        cx2 = start + cw + gap;
        cx3 = start + (cw + gap) * 2.0F;
        greetY = Math.max(16.0F, cy - 30.0F);
        bh = 14.0F;
        by = cy + ch + Math.max(10.0F, safeH * 0.028F);
        bw1 = 0; bw2 = 0;
        bx1 = bx2 = 0;
    }

    public void sizeOfBarPill(Text label, float x) {
        // helper slot reserved
    }

    public void draw(DrawContext ctx, MinecraftClient client, int mouseX, int mouseY, long now) {
        float intro = intro(0L, 520L);
        if (intro <= 0.0F) intro = 0.01F;
        // small brand in the top-left corner
        if (intro > 0.001F) {
            Render2D.pushAlpha(intro);
            Text brand = Text.literal("NEXUS").setStyle(net.minecraft.text.Style.EMPTY.withBold(true));
            TextRender.draw(ctx, brand, 10.0F, 10.0F, -1, false);
            Render2D.popAlpha();
        }
        // greeting, like Sky DLC: big centered line "Good to see you, {name}"
        float gt = intro(180L, 500L);
        if (gt > 0.0F) {
            Render2D.pushAlpha(gt);
            String name = client.getSession().getUsername();
            String greet = I18n.translate("nexus.title.sky.greeting", name);
            int gw = TextRender.width(Text.literal(greet));
            float scale = 2.1F;
            TextRender.drawScaled(ctx, Text.literal(greet), ctxWidth(ctx) / 2.0F - gw * scale / 2.0F, greetY + (1.0F - gt) * 4.0F, scale, -1, false);
            String hint = I18n.translate("nexus.title.sky.hint");
            int hw = TextRender.width(Text.literal(hint));
            TextRender.draw(ctx, Text.literal(hint), ctxWidth(ctx) / 2.0F - hw / 2.0F, greetY + 22.0F, -6710887, false);
            Render2D.popAlpha();
        }
        // three cards
        card(ctx, 0, cx1, cy, cw, ch, "nexus.title.sky.card1", bg(1), mouseX, mouseY, intro(300L, 520L), false);
        card(ctx, 1, cx2, cy, cw, ch, "nexus.title.sky.card2", bg(2), mouseX, mouseY, intro(380L, 520L), true);
        card(ctx, 2, cx3, cy, cw, ch, "nexus.title.sky.card3", bg(3), mouseX, mouseY, intro(460L, 520L), false);
        barPill(ctx, 0, "menu.options", mouseX, mouseY, intro(560L, 420L));
        barPill(ctx, 1, "menu.quit", mouseX, mouseY, intro(620L, 420L));
    }

    private static int ctxWidth(DrawContext ctx) {
        return ctx.getScaledWindowWidth();
    }

    private long intro(long delayMs, long durationMs) {
        return Math.round(1000.0F * tOf(delayMs, durationMs)) / 1000L;
    }

    private float tOf(long delayMs, long durationMs) {
        float t = (float)(net.minecraft.util.Util.getMeasuringTimeMs() - openStamp - delayMs) / (float)durationMs;
        t = Math.max(0.0F, Math.min(1.0F, t));
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private static Identifier bg(int n) {
        String name = switch (n) {
            case 1 -> "backgrounds/bg_02.jpg";
            case 2 -> "backgrounds/bg_06.jpg";
            default -> "backgrounds/bg_08.jpg";
        };
        return Identifier.of("nexus", name);
    }

    private final int[] dims = new int[2];

    private void card(DrawContext ctx, int idx, float x, float y, float w, float h, String key, Identifier tex,
                      int mouseX, int mouseY, float t, boolean primary) {
        if (t <= 0.0F) return;
        Render2D.pushAlpha(t);
        float slideA = (1.0F - t) * 10.0F;
        x += slideA;
        boolean hover = mouseX >= x && mouseY >= y && mouseX <= x + w && mouseY <= y + h;
        Render2D.rect(ctx, x, y, w, h, 0xFF0B0C12);
        // photo taken from the same decoder pipeline as the menu slideshow (JPG-safe), cover-fit into the card
        Identifier ph = dev.nexus.client.ui.vanilla.MenuBackground.get().cardPhoto(idx, this.dims);
        if (ph != null && this.dims[0] > 0 && this.dims[1] > 0) {
            ctx.enableScissor(Math.round(x) + 1, Math.round(y) + 1, Math.round(x + w) - 1, Math.round(y + h) - 1);
            float sc = Math.max(w / (float)this.dims[0], h / (float)this.dims[1]);
            float dw = this.dims[0] * sc;
            float dh = this.dims[1] * sc;
            org.joml.Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(x + w / 2.0F - dw / 2.0F, y + h / 2.0F - dh / 2.0F);
            m.scale(sc, sc);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ph, 0, 0, 0.0F, 0.0F,
                    this.dims[0], this.dims[1], this.dims[0], this.dims[1], this.dims[0], this.dims[1], -1);
            m.popMatrix();
            ctx.disableScissor();
        } else {
            // still decoding: tinted fallback block with an accent square, same vibe as the photo
            Render2D.roundedRect(ctx, x, y, w, h, 5.0F, -12829636);
            Render2D.roundedRect(ctx, x + w / 2.0F - 11.0F, y + h * 0.34F, 22.0F, 22.0F, 6.0F, -16711712);
        }
        // bottom dark gradient for captions
        Render2D.gradientV(ctx, x + 1, y + h * 0.45F, w - 2, h * 0.55F, 0, -1845493760);
        Render2D.roundedOutline(ctx, x, y, w, h, 5.0F, 1.0F, hover ? -788330497 : -956301829);
        String cap = I18n.translate(key);
        Text capText = Text.literal(cap);
        int capW = TextRender.width(capText);
        TextRender.draw(ctx, capText, x + w / 2.0F - capW / 2.0F, y + h - (primary ? 34.0F : 20.0F), -1, true);
        if (primary) {
            String btn = I18n.translate("nexus.title.sky.play");
            int bww = Math.max(40, TextRender.widthRaw(btn) + 14);
            float bx = x + w / 2.0F - bww / 2.0F;
            float bpY = y + h - 18.0F;
            boolean bHover = mouseX >= bx && mouseY >= bpY && mouseX <= bx + bww && mouseY <= bpY + 12;
            Render2D.roundedRect(ctx, bx, bpY, bww, 12.0F, 5.0F, bHover ? 0xFFFFFFFF : 0xFFF2F3F8);
            TextRender.draw(ctx, Text.literal(btn), bx + bww / 2.0F - TextRender.width(Text.literal(btn)) / 2.0F, bpY + 2.5F, 0xFF14151D, false);
        }
        Render2D.popAlpha();
    }

    private void barPill(DrawContext ctx, int idx, String key, int mouseX, int mouseY, float t) {
        if (t <= 0.0F) return;
        Render2D.pushAlpha(t);
        String s = I18n.translate(key);
        Text text = Text.literal(s);
        int tw = TextRender.widthRaw(s) + 14;
        float x = ctxWidth(ctx) / 2.0F + (idx == 0 ? -tw - 5.0F : 5.0F);
        float y = Math.min(cy + ch + 10.0F, ctx.getScaledWindowHeight() - 30.0F);
        boolean hover = mouseX >= x && mouseY >= y && mouseX <= x + tw && mouseY <= y + 14;
        Render2D.roundedRect(ctx, x, y, tw, 14.0F, 6.0F, hover ? 0xC81B1D27 : 0x961B1D27);
        TextRender.draw(ctx, text, x + tw / 2.0F - TextRender.width(text) / 2.0F, y + 3.5F, idx == 1 ? -1314018 : -8969811, false);
        if (idx == 0) { bx1 = x; bw1 = tw; } else { bx2 = x; bw2 = tw; }
        Render2D.popAlpha();
    }

    /** Returns true when the click was consumed by a card or a pill. */
    public boolean click(net.minecraft.client.gui.Click input) {
        return clickAt((int)input.x(), (int)input.y(), input);
    }

    private boolean clickAt(int mouseX, int mouseY, net.minecraft.client.gui.Click input) {
        if (hit(mouseX, mouseY, cx2 + cw * 0.12F, cy + ch - 18.0F, cw * 0.76F, 12.0F) || hit(mouseX, mouseY, cx2, cy, cw, ch)) {
            press(multi, input);
            return true;
        }
        if (hit(mouseX, mouseY, cx1, cy, cw, ch)) { press(single, input); return true; }
        if (hit(mouseX, mouseY, cx3, cy, cw, ch)) { if (openNexus != null) openNexus.run(); return true; }
        if (hit(mouseX, mouseY, bx1, by, bw1, bh)) { press(options, input); return true; }
        if (hit(mouseX, mouseY, bx2, by, bw2, bh)) { press(quit, input); return true; }
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
