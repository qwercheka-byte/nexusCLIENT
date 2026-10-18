package dev.nexus.client.ui;

import dev.nexus.client.util.Lang;
import dev.nexus.client.Nexus;
import dev.nexus.client.module.Module;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.Scissor;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.animation.Easing;
import dev.nexus.client.ui.animation.Fade;
import dev.nexus.client.ui.animation.PageTransition;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.Component;
import dev.nexus.client.ui.page.AboutPage;
import dev.nexus.client.ui.page.HomePage;
import dev.nexus.client.ui.page.HudPage;
import dev.nexus.client.ui.page.ModulesPage;
import dev.nexus.client.ui.page.Page;
import dev.nexus.client.ui.page.ProfilesPage;
import dev.nexus.client.ui.page.SettingsPage;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import dev.nexus.client.util.Mc;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

public final class NexusScreen extends Screen {
    public static final int HOME = 0;
    public static final int MODULES = 1;
    public static final int HUD = 2;
    public static final int PROFILES = 3;
    public static final int SETTINGS = 4;
    public static final int ABOUT = 5;
    private static int lastPage = 0;
    private static final float MIN_W = 470.0F;
    private static final float MIN_H = 300.0F;
    private static final float HEADER_H = 44.0F;
    private static final Text LOGO_TEXT = Text.literal("NEXUS").setStyle(Style.EMPTY.withBold(true));
    private final Page[] pages;
    private int current;
    /** Page switch animation (fade + slide); the global animation speed is applied inside the animation system. */
    private final PageTransition pageTransition;
    /** Window fade-in when opened and fade-out before closing. */
    private final Fade openFade = new Fade(220L, 160L, Easing.OUT_CUBIC);
    private final SmoothValue[] navHover;
    private final SmoothValue navIndicator;
    private final SmoothValue closeHover = new SmoothValue(0.0F, 18.0F);
    private final SmoothValue profileHover = new SmoothValue(0.0F, 18.0F);
    private final long openedAt = System.currentTimeMillis();
    private boolean closing;
    private float scale = 1.0F;
    private float vw;
    private float vh;
    private double layoutUiScale = -1.0;
    private float winX;
    private float winY;
    private float winW;
    private float winH;
    private float sideW;
    private boolean compact;
    private static final float NAV_TOP = 54.0F;
    private static final float NAV_STEP = 28.0F;
    private static final float NAV_H = 24.0F;

    public NexusScreen() {
        this(lastPage);
    }

    public NexusScreen(int page) {
        super(Text.literal("NEXUS"));
        this.pages = new Page[]{
            new HomePage(this), new ModulesPage(this), new HudPage(this), new ProfilesPage(this), new SettingsPage(this), new AboutPage(this)
        };
        this.current = MathUtil.clamp(page, 0, this.pages.length - 1);
        this.navHover = new SmoothValue[this.pages.length];

        for (int i = 0; i < this.pages.length; i++) {
            this.navHover[i] = new SmoothValue(0.0F, 18.0F);
        }

        this.navIndicator = new SmoothValue(this.current, 14.0F);
        this.pageTransition = new PageTransition(this.current, 220L, Easing.OUT_CUBIC, 10.0F);
        UiContext.reset();
        this.openFade.restartIn();
        this.pages[this.current].onShow();
    }

    @Override
    protected void init() {
        this.layoutUiScale = Nexus.settings().uiScale.get();
        float auto = Math.min(1.0F, Math.min(this.width / 490.0F, this.height / 320.0F));
        this.scale = Math.max(0.3F, auto * Nexus.settings().uiScale.getFloat());
        this.vw = this.width / this.scale;
        this.vh = this.height / this.scale;
        this.winW = Math.min(700.0F, this.vw - 20.0F);
        this.winH = Math.min(420.0F, this.vh - 20.0F);
        this.winX = (this.vw - this.winW) / 2.0F;
        this.winY = (this.vh - this.winH) / 2.0F;
        this.compact = this.winW < 500.0F;
        this.sideW = this.compact ? 42.0F : 140.0F;
        float cx = this.winX + this.sideW + 16.0F;
        float cy = this.winY + 44.0F + 10.0F;

        for (Page p : this.pages) {
            p.layout(cx, cy, this.winX + this.winW - 14.0F - cx, this.winY + this.winH - 12.0F - cy);
        }
    }

    public float virtualWidth() {
        return this.vw;
    }

    public float virtualHeight() {
        return this.vh;
    }

    public void showPage(int index) {
        if (index != this.current && index >= 0 && index < this.pages.length) {
            UiContext.unfocus(UiContext.focused());
            this.current = index;
            lastPage = index;
            this.pageTransition.show(index);
            this.pages[this.current].onShow();
        }
    }

    public void openModuleSettings(Module module) {
        ((ModulesPage)this.pages[1]).openSettings(module, this.current != 1 ? this.current : -1);
        this.showPage(1);
    }

    public void openHudEditor() {
        if (this.client != null) {
            this.client.setScreen(new HudEditorScreen(this));
        }
    }

    private void beginClose() {
        if (!this.closing) {
            this.closing = true;
            UiContext.reset();
            this.openFade.fadeOut();
        }
    }

    @Override
    public void close() {
        this.beginClose();
    }

    @Override
    public void removed() {
        UiContext.reset();
        Nexus.config().flushAsync();
    }

    @Override
    public boolean shouldPause() {
        return Nexus.settings().pauseInMenu.isOn();
    }

    private float openProgress() {
        return this.openFade.alpha();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (this.closing && this.openFade.isFadedOut()) {
            if (this.client != null) {
                this.client.setScreen(null);
            }
        } else {
            if (Nexus.settings().uiScale.get() != this.layoutUiScale && UiContext.dragging() == null) {
                this.init();
            }

            Render2D.resetAlpha();
            UiContext.beginFrame();
            float open = this.openProgress();
            if (this.client != null && this.client.world == null) {
                Render2D.rect(context, 0.0F, 0.0F, this.width, this.height, -267514093);
            } else if (Nexus.settings().backgroundBlur.isOn() && open > 0.5F) {
                this.applyBlur(context);
            }

            int dim = (int)(Nexus.settings().backgroundDim.get() * 255.0 * open);
            Render2D.gradientV(
                context, 0.0F, 0.0F, this.width, this.height, ColorUtil.withAlpha(-16316661, dim), ColorUtil.withAlpha(-15921900, Math.min(255, dim + 20))
            );
            double vmx = this.toVirtualX(mouseX);
            double vmy = this.toVirtualY(mouseY);
            if (this.closing) {
                vmx = -10000.0;
                vmy = -10000.0;
            }

            Matrix3x2fStack m = context.getMatrices();
            m.pushMatrix();
            m.scale(this.scale, this.scale);
            float zoom = 0.96F + 0.04F * open;
            float pcx = this.winX + this.winW / 2.0F;
            float pcy = this.winY + this.winH / 2.0F;
            m.translate(pcx, pcy);
            m.scale(zoom, zoom);
            m.translate(-pcx, -pcy + (1.0F - open) * 6.0F);
            Render2D.pushAlpha(open);
            this.drawWindow(context);
            this.drawSidebar(context, vmx, vmy);
            this.drawHeader(context, vmx, vmy);
            this.drawPages(context, vmx, vmy);
            UiContext.renderTooltip(context, this.vw, this.vh);
            Render2D.popAlpha();
            m.popMatrix();
            Nexus.notifications().render(context, this.width, this.height);
        }
    }

    private double toVirtualX(double mx) {
        float zoom = 0.96F + 0.04F * this.openProgress();
        float pcx = this.winX + this.winW / 2.0F;
        return (mx / this.scale - pcx) / zoom + pcx;
    }

    private double toVirtualY(double my) {
        float open = this.openProgress();
        float zoom = 0.96F + 0.04F * open;
        float pcy = this.winY + this.winH / 2.0F;
        return (my / this.scale - pcy) / zoom + pcy - (1.0F - open) * 6.0F;
    }

    private void drawWindow(DrawContext ctx) {
        Render2D.shadow(
            ctx,
            this.winX + this.winW * 0.1F,
            this.winY + this.winH * 0.15F,
            this.winW * 0.3F,
            this.winH * 0.3F,
            40.0F,
            120.0F,
            ColorUtil.withAlpha(Theme.accent(), 26)
        );
        Render2D.shadow(
            ctx,
            this.winX + this.winW * 0.6F,
            this.winY + this.winH * 0.55F,
            this.winW * 0.3F,
            this.winH * 0.3F,
            40.0F,
            110.0F,
            ColorUtil.withAlpha(-13058568, 18)
        );
        Render2D.shadow(ctx, this.winX, this.winY + 6.0F, this.winW, this.winH, 10.0F, 22.0F, -1610612736);
        Render2D.roundedRect(ctx, this.winX, this.winY, this.winW, this.winH, 10.0F, -200339436);
        Render2D.roundedRect(ctx, this.winX, this.winY, this.sideW, this.winH, 10.0F, 0.0F, 0.0F, 10.0F, -133493744);
        Render2D.rect(ctx, this.winX + this.sideW, this.winY + 8.0F, 1.0F, this.winH - 16.0F, 318767103);
        Render2D.roundedOutline(ctx, this.winX, this.winY, this.winW, this.winH, 10.0F, 1.0F, 452984831);
        float lw = this.winW * 0.5F;
        Render2D.gradientH(ctx, this.winX + 10.0F, this.winY, lw - 10.0F, 1.0F, ColorUtil.withAlpha(Theme.accent(), 0), Theme.accent());
        Render2D.gradientH(ctx, this.winX + lw, this.winY, lw - 10.0F, 1.0F, Theme.accent(), ColorUtil.withAlpha(Theme.accent(), 0));
    }

    private float navItemY(int i) {
        return this.winY + 54.0F + i * 28.0F;
    }

    private float chipY() {
        return this.winY + this.winH - 38.0F;
    }

    private void drawSidebar(DrawContext ctx, double mx, double my) {
        float bx = this.winX + (this.compact ? (this.sideW - 22.0F) / 2.0F : 12.0F);
        float by = this.winY + 14.0F;
        Render2D.shadow(ctx, bx + 3.0F, by + 6.0F, 16.0F, 16.0F, 6.0F, 8.0F, ColorUtil.withAlpha(Theme.accent(), 80));
        Render2D.roundedGradient(ctx, bx, by, 22.0F, 22.0F, 6.0F, Theme.accent(), ColorUtil.lerp(Theme.accent(), -13058568, 0.6F), true);
        Render2D.roundedRect(ctx, bx + 1.0F, by + 1.0F, 20.0F, 10.0F, 5.0F, 5.0F, 0.0F, 0.0F, 587202559);
        IconRender.drawCentered(ctx, Icon.LOGO, bx + 11.0F, by + 11.0F, 14.0F, Theme.onAccent());
        if (!this.compact) {
            TextRender.drawScaled(ctx, LOGO_TEXT, bx + 29.0F, by + 2.0F, 1.15F, -1249805, false);
            TextRender.drawScaled(ctx, "CLIENT  " + Nexus.VERSION, bx + 29.0F, by + 14.0F, 0.68F, -10460550, false);
        }

        float ind = this.navIndicator.update(this.current);
        float itemX = this.winX + 8.0F;
        float itemW = this.sideW - 16.0F;
        float itemH = 24.0F;
        float iy = this.winY + 54.0F + ind * 28.0F;
        Render2D.roundedRect(ctx, itemX, iy, itemW, itemH, 6.0F, ColorUtil.withAlpha(Theme.accent(), 38));
        Render2D.roundedOutline(ctx, itemX, iy, itemW, itemH, 6.0F, 1.0F, ColorUtil.withAlpha(Theme.accent(), 48));

        for (int i = 0; i < this.pages.length; i++) {
            float y = this.navItemY(i);
            boolean hov = MathUtil.inside(mx, my, itemX, y, itemW, itemH);
            float h = this.navHover[i].update(hov ? 1.0F : 0.0F);
            if (h > 0.01F && i != this.current) {
                Render2D.roundedRect(ctx, itemX, y, itemW, itemH, 6.0F, ColorUtil.withAlpha(-1, (int)(12.0F * h)));
            }

            float sel = Math.max(0.0F, 1.0F - Math.abs(ind - i));
            int c = ColorUtil.lerp(ColorUtil.lerp(-6644563, -1249805, h), Theme.accentBright(), sel);
            if (this.compact) {
                IconRender.drawCentered(ctx, this.pages[i].icon(), itemX + itemW / 2.0F, y + itemH / 2.0F, 11.0F, c);
                if (hov) {
                    UiContext.tooltip(this.pages[i].title(), mx, my);
                }
            } else {
                IconRender.draw(ctx, this.pages[i].icon(), itemX + 9.0F, y + (itemH - 12.0F) / 2.0F, 12.0F, c);
                TextRender.draw(
                    ctx,
                    this.pages[i].title(),
                    itemX + 29.0F,
                    TextRender.centerY(y, itemH),
                    ColorUtil.lerp(ColorUtil.lerp(-6644563, -1249805, h), -1249805, sel)
                );
            }
        }

        float py = this.chipY();
        float px = this.winX + 8.0F;
        float pw = this.sideW - 16.0F;
        float chipH = 30.0F;
        boolean phov = MathUtil.inside(mx, my, px, py, pw, chipH);
        float ph = this.profileHover.update(phov ? 1.0F : 0.0F);
        Render2D.roundedRect(ctx, px, py, pw, chipH, 6.0F, ColorUtil.lerp(268435455, 486539263, ph));
        Render2D.roundedOutline(ctx, px, py, pw, chipH, 6.0F, 1.0F, ColorUtil.lerp(285212671, ColorUtil.withAlpha(Theme.accent(), 128), ph));
        String profile = Nexus.config().profiles().getActive();
        float head = 18.0F;
        float hx = this.compact ? px + (pw - head) / 2.0F : px + 6.0F;
        float hy = py + (chipH - head) / 2.0F;
        Render2D.roundedRect(ctx, hx - 1.0F, hy - 1.0F, head + 2.0F, head + 2.0F, 3.0F, Theme.accent());
        if (this.client != null) {
            SkinTextures skin = this.client.player != null
                    && this.client.getNetworkHandler() != null
                    && this.client.getNetworkHandler().getPlayerListEntry(this.client.player.getUuid()) != null
                ? this.client.getNetworkHandler().getPlayerListEntry(this.client.player.getUuid()).getSkinTextures()
                : DefaultSkinHelper.getSkinTextures(this.client.getGameProfile());
            PlayerSkinDrawer.draw(ctx, skin, Math.round(hx), Math.round(hy), (int)head);
        }

        if (this.compact) {
            if (phov) {
                UiContext.tooltip(Lang.fmt("Profile: {0}", profile), mx, my);
            }
        } else {
            String name = this.client != null ? this.client.getSession().getUsername() : "Player";
            TextRender.draw(ctx, TextRender.ellipsize(name, (int)pw - 34), px + 30.0F, py + 6.0F, -1249805);
            TextRender.drawScaled(ctx, TextRender.ellipsizeRaw(Lang.fmt("Profile · {0}", profile), (int)((pw - 34.0F) / 0.7F)), px + 30.0F, py + 17.0F, 0.7F, -10460550, false);
        }
    }

    private void drawHeader(DrawContext ctx, double mx, double my) {
        Page p = this.pages[this.current];
        float hx = this.winX + this.sideW + 16.0F;
        float hy = this.winY + 10.0F;
        TextRender.drawScaled(ctx, p.title(), hx, hy, 1.5F, -1249805, false);
        TextRender.draw(ctx, TextRender.ellipsize(p.subtitle(), (int)(this.winW - this.sideW - 64.0F)), hx, hy + 18.0F, -10460550);
        float cx = this.winX + this.winW - 28.0F;
        float cy = this.winY + 12.0F;
        boolean hov = MathUtil.inside(mx, my, cx, cy, 18.0F, 18.0F);
        float h = this.closeHover.update(hov ? 1.0F : 0.0F);
        if (h > 0.01F) {
            Render2D.roundedRect(ctx, cx, cy, 18.0F, 18.0F, 4.0F, ColorUtil.withAlpha(-495247, (int)(40.0F * h)));
        }

        IconRender.drawCentered(ctx, Icon.CLOSE, cx + 9.0F, cy + 9.0F, 9.0F, ColorUtil.lerp(-6644563, -219739, h));
        if (hov) {
            UiContext.tooltip("Close (Esc)", mx, my);
        }

        Render2D.rect(ctx, this.winX + this.sideW + 1.0F, this.winY + 44.0F, this.winW - this.sideW - 1.0F, 1.0F, 318767103);
    }

    private void drawPages(DrawContext ctx, double mx, double my) {
        float cx = this.winX + this.sideW + 1.0F;
        float cy = this.winY + 44.0F + 1.0F;
        float cw = this.winW - this.sideW - 1.0F;
        float ch = this.winH - 44.0F - 2.0F;
        Scissor.push(ctx, cx, cy, cw, ch);
        UiContext.pushClip(cx, cy, cw, ch);
        Matrix3x2fStack m = ctx.getMatrices();
        int previous = this.pageTransition.previous();
        if (previous >= 0) {
            // outgoing page: no mouse interaction, fades out while sliding away
            m.pushMatrix();
            m.translate(0.0F, this.pageTransition.outgoingOffset());
            Render2D.pushAlpha(this.pageTransition.outgoingAlpha());
            this.pages[previous].render(ctx, -10000.0, -10000.0);
            Render2D.popAlpha();
            m.popMatrix();
        }

        float offset = this.pageTransition.incomingOffset();
        m.pushMatrix();
        m.translate(0.0F, offset);
        Render2D.pushAlpha(this.pageTransition.incomingAlpha());
        this.pages[this.current].render(ctx, mx, my - offset);
        Render2D.popAlpha();
        m.popMatrix();
        UiContext.popClip();
        Scissor.pop(ctx);
    }

    private boolean inContent(double mx, double my) {
        return MathUtil.inside(mx, my, this.winX + this.sideW + 1.0F, this.winY + 44.0F + 1.0F, this.winW - this.sideW - 1.0F, this.winH - 44.0F - 2.0F);
    }

    private boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (this.closing) {
            return true;
        } else {
            double mx = this.toVirtualX(mouseX);
            double my = this.toVirtualY(mouseY);
            Component focused = UiContext.focused();
            if (focused != null && focused.mouseClicked(mx, my, button)) {
                return true;
            } else {
                if (button == 0) {
                    float itemX = this.winX + 8.0F;
                    float itemW = this.sideW - 16.0F;

                    for (int i = 0; i < this.pages.length; i++) {
                        if (MathUtil.inside(mx, my, itemX, this.navItemY(i), itemW, 24.0F)) {
                            if (i != this.current) {
                                Button.playClick();
                            }

                            this.showPage(i);
                            return true;
                        }
                    }

                    if (MathUtil.inside(mx, my, this.winX + 8.0F, this.chipY(), this.sideW - 16.0F, 30.0F)) {
                        Button.playClick();
                        this.showPage(3);
                        return true;
                    }

                    if (MathUtil.inside(mx, my, this.winX + this.winW - 28.0F, this.winY + 12.0F, 18.0F, 18.0F)) {
                        Button.playClick();
                        this.beginClose();
                        return true;
                    }
                }

                if (this.inContent(mx, my)) {
                    UiContext.pushClip(this.winX + this.sideW + 1.0F, this.winY + 44.0F + 1.0F, this.winW - this.sideW - 1.0F, this.winH - 44.0F - 2.0F);

                    try {
                        if (this.pages[this.current].mouseClicked(mx, my, button)) {
                            return true;
                        }
                    } finally {
                        UiContext.popClip();
                    }
                }

                if (focused != null && UiContext.focused() == focused) {
                    UiContext.unfocus(focused);
                }

                return false;
            }
        }
    }

    private boolean onMouseReleased(double mouseX, double mouseY, int button) {
        double mx = this.toVirtualX(mouseX);
        double my = this.toVirtualY(mouseY);
        Component drag = UiContext.dragging();
        if (drag != null) {
            drag.mouseReleased(mx, my, button);
            UiContext.stopDrag();
        }

        this.pages[this.current].mouseReleased(mx, my, button);
        return true;
    }

    private boolean onMouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        Component drag = UiContext.dragging();
        return drag != null ? drag.mouseDragged(this.toVirtualX(mouseX), this.toVirtualY(mouseY), button) : false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.closing) {
            return true;
        } else {
            double mx = this.toVirtualX(mouseX);
            double my = this.toVirtualY(mouseY);
            if (MathUtil.inside(mx, my, this.winX, this.winY, this.sideW, this.winH) && verticalAmount != 0.0) {
                this.showPage(MathUtil.clamp(this.current + (verticalAmount < 0.0 ? 1 : -1), 0, this.pages.length - 1));
                return true;
            } else if (this.inContent(mx, my)) {
                UiContext.pushClip(this.winX + this.sideW + 1.0F, this.winY + 44.0F + 1.0F, this.winW - this.sideW - 1.0F, this.winH - 44.0F - 2.0F);

                boolean var13;
                try {
                    var13 = this.pages[this.current].mouseScrolled(mx, my, verticalAmount);
                } finally {
                    UiContext.popClip();
                }

                return var13;
            } else {
                return false;
            }
        }
    }

    private boolean onKeyPressed(int key, int scancode, int modifiers) {
        if (this.closing) {
            return true;
        } else {
            Component focused = UiContext.focused();
            if (focused != null) {
                focused.keyPressed(key, scancode, modifiers);
                return true;
            } else if (this.pages[this.current].keyPressed(key, scancode, modifiers)) {
                return true;
            } else if (key == 256) {
                if (!this.pages[this.current].back()) {
                    this.beginClose();
                }

                return true;
            } else if (Nexus.settings().menuKey.matchesKey(key) && System.currentTimeMillis() - this.openedAt > 200L) {
                this.beginClose();
                return true;
            } else if (Mc.ctrl(modifiers) && key == 70) {
                this.showPage(1);
                ((ModulesPage)this.pages[1]).focusSearch();
                return true;
            } else if (Mc.ctrl(modifiers) && key >= 49 && key <= 54) {
                this.showPage(key - 49);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean onCharTyped(char chr, int modifiers) {
        if (this.closing) {
            return true;
        } else {
            Component focused = UiContext.focused();
            return focused != null ? focused.charTyped(chr, modifiers) : this.pages[this.current].charTyped(chr, modifiers);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return this.onMouseClicked(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(Click click) {
        return this.onMouseReleased(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        return this.onMouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return this.onKeyPressed(input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean charTyped(CharInput input) {
        boolean handled = false;

        for (char c : Character.toChars(input.codepoint())) {
            handled |= this.onCharTyped(c, input.modifiers());
        }

        return handled;
    }
}
