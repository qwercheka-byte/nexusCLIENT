package dev.nexus.client.mixin;

import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.vanilla.MenuBackground;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import dev.nexus.client.ui.vanilla.TitleDecor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.client.realms.gui.screen.RealmsNotificationsScreen;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow
    @Nullable
    private SplashTextRenderer splashText;
    @Shadow
    @Nullable
    private RealmsNotificationsScreen realmsNotificationGui;
    @Unique
    private final TitleDecor nexus$decor = new TitleDecor();
    @Unique
    private final Map<ClickableWidget, Integer> nexus$baseY = new IdentityHashMap<>();
    @Unique
    private final List<ClickableWidget> nexus$animated = new ArrayList<>();
    @Unique
    @Nullable
    private ClickableWidget nexus$realms;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private static String nexus$key(ClickableWidget w) {
        return w.getMessage().getContent() instanceof TranslatableTextContent t ? t.getKey() : "";
    }

    @Unique
    @Nullable
    private ButtonWidget nexus$button;
    @Unique
    private boolean nexus$needsLayout;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void nexus$init(CallbackInfo ci) {
        if (this.client != null) {
            this.nexus$decor.initSkin(this.client);
            MenuBackground.get().refresh(this.client);
            this.nexus$button = ButtonWidget.builder(Text.translatable("nexus.title.open"), b -> this.client.setScreen(new NexusScreen())).build();
            NexusWidgets.setStyle(this.nexus$button, NexusWidgets.Style.BRAND);
            this.addDrawableChild(this.nexus$button);
            // Other mods (e.g. Mod Menu) add or move title buttons in Fabric's ScreenEvents.AFTER_INIT, which runs
            // after this method. The layout is therefore done on the first frame, when all buttons exist.
            this.nexus$needsLayout = true;
            this.nexus$animated.clear();
            this.nexus$baseY.clear();
        }
    }

    @Unique
    private void nexus$layout() {
        List<ClickableWidget> column = new ArrayList<>();
        List<ClickableWidget> icons = new ArrayList<>();
        ClickableWidget options = null;
        ClickableWidget quit = null;
        ClickableWidget primary = null;
        this.nexus$realms = null;

        for (Element e : this.children()) {
            if (!(e instanceof ClickableWidget w) || w == this.nexus$button || !w.visible) continue;
            if (w instanceof PressableTextWidget) continue; // vanilla bottom lines are drawn by TitleDecor.footer
            String key = nexus$key(w);
            switch (key) {
                case "menu.singleplayer":
                case "menu.playdemo":
                    column.add(w);
                    primary = w;
                    break;
                case "menu.online":
                    column.add(w);
                    this.nexus$realms = w;
                    break;
                case "menu.options":
                    options = w;
                    break;
                case "menu.quit":
                    quit = w;
                    break;
                default:
                    if (w instanceof TextIconButtonWidget || w.getWidth() <= 24) {
                        icons.add(w);
                    } else if (w instanceof ButtonWidget && w.getHeight() >= 14
                            && !w.getMessage().getString().contains("Mojang AB")) {
                        // multiplayer, demo reset, Mod Menu's "Mods" and buttons of other mods
                        column.add(w);
                    }
            }
        }

        column.sort(Comparator.comparingInt(ClickableWidget::getY).thenComparingInt(ClickableWidget::getX));
        icons.sort(Comparator.comparingInt(ClickableWidget::getX));
        if (this.nexus$button != null) column.add(this.nexus$button);
        if (options != null) column.add(options);
        if (primary != null) {
            NexusWidgets.setStyle(primary, NexusWidgets.Style.PRIMARY);
        }

        int bw = 170;
        int bh = 20;
        int step = 24;
        int iconRowH = icons.isEmpty() ? 0 : 24;
        int buttonsH = column.size() * step + iconRowH;
        float logoH = this.height >= 300 ? 88.0F : 56.0F;
        boolean greet = this.height >= 240;
        float greetH = greet ? 16.0F : 0.0F;
        if (logoH + greetH + buttonsH > this.height - 40) {
            greet = false;
            greetH = 0.0F;
        }
        float total = logoH + greetH + buttonsH;
        float top = Math.max(6.0F, (this.height - total) / 2.0F - this.height * 0.03F);
        this.nexus$decor.logoTop = top;
        this.nexus$decor.logoHeight = logoH;
        this.nexus$decor.greetingY = top + logoH;
        this.nexus$decor.showGreeting = greet;
        int x = this.width / 2 - bw / 2;
        int y = Math.round(top + logoH + greetH);
        this.nexus$animated.clear();
        this.nexus$baseY.clear();

        for (ClickableWidget w : column) {
            w.setDimensionsAndPosition(bw, bh, x, y);
            this.nexus$track(w, y);
            y += step;
        }

        // centred row of small square icon buttons (language, accessibility, …)
        if (!icons.isEmpty()) {
            y += 2;
            int iw = icons.size() * 24 - 4;
            int ix = this.width / 2 - iw / 2;
            for (ClickableWidget icon : icons) {
                icon.setPosition(ix, y);
                this.nexus$track(icon, y);
                ix += 24;
            }
        }

        // exit: small pill at the bottom centre, the version strings sit below it
        if (quit != null) {
            int qw = 104;
            int qy = this.height - 34;
            quit.setDimensionsAndPosition(qw, bh, this.width / 2 - qw / 2, qy);
            this.nexus$track(quit, qy);
        }
    }

    @Unique
    private void nexus$drawLabel(DrawContext ctx, ClickableWidget w, Text msg) {
        Render2D.pushAlpha(w.getAlpha());
        int color = w.active ? 0xFFFFFFFF : 0xFF9AA0AC;
        boolean brand = NexusWidgets.style(w) == NexusWidgets.Style.BRAND;
        // brand buttons show the hexagon icon on the left: nudge the label right of centre
        float cx = w.getX() + w.getWidth() / 2.0F + (brand ? 7.0F : 0.0F);
        float cy = w.getY() + (w.getHeight() - 8.0F) / 2.0F;
        int tw = TextRender.width(msg);
        TextRender.draw(ctx, msg, cx - tw / 2.0F, cy, color, false);
        Render2D.popAlpha();
    }

    @Unique
    private void nexus$track(ClickableWidget w, int y) {
        this.nexus$baseY.put(w, y);
        this.nexus$animated.add(w);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
    private void nexus$render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();
        if (this.client != null) {
            if (this.nexus$needsLayout) {
                this.nexus$needsLayout = false;
                this.nexus$layout();
            }
            boolean photos = MenuBackground.get().render(ctx, this.width, this.height);
            if (!photos) {
                this.renderPanoramaBackground(ctx, delta);
                this.applyBlur(ctx);
            }
            this.nexus$decor.overlay(ctx, this.width, this.height, photos);
            boolean splash = !this.client.options.getHideSplashTexts().getValue();
            this.nexus$decor.logo(ctx, this.width, this.splashText != null ? ((SplashTextRendererAccessor)this.splashText).nexus$text() : null, splash);
            this.nexus$decor.greeting(ctx, this.client, this.width);

            for (int i = 0; i < this.nexus$animated.size(); i++) {
                ClickableWidget w = this.nexus$animated.get(i);
                float t = this.nexus$decor.intro(260L + i * 55L, 420L);
                w.setAlpha(t);
                Integer base = this.nexus$baseY.get(w);
                if (base != null) {
                    w.setY(base + Math.round((1.0F - t) * 8.0F));
                }
            }

            for (Drawable d : ((ScreenAccessor)this).nexus$drawables()) {
                // both vanilla copyright widgets are replaced by a single line in TitleDecor.footer
                if (d instanceof PressableTextWidget
                        || (d instanceof ClickableWidget cw0 && cw0.getMessage().getString().contains("Mojang AB"))) {
                    continue;
                }
                if (d instanceof ButtonWidget w && !(d instanceof TextIconButtonWidget) && w.visible && w.getHeight() >= 14 && !w.getMessage().getString().isEmpty()) {
                    // vanilla paints button labels with the pixel font: hide it, then draw the label with the UI typeface
                    Text msg = w.getMessage();
                    w.setMessage(Text.empty());
                    d.render(ctx, mouseX, mouseY, delta);
                    w.setMessage(msg);
                    this.nexus$drawLabel(ctx, w, msg);
                } else {
                    d.render(ctx, mouseX, mouseY, delta);
                }
            }

            this.nexus$decor.footer(ctx, this.client, this.width, this.height);
            if (this.realmsNotificationGui != null && this.nexus$realms != null && this.nexus$decor.intro(600L, 1L) >= 1.0F) {
                int dx = this.nexus$realms.getRight() - (this.width / 2 + 100);
                int dy = this.nexus$realms.getY() - (this.height / 4 + 48 + 48);
                Matrix3x2fStack m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(dx, dy);
                this.realmsNotificationGui.render(ctx, mouseX - dx, mouseY - dy, delta);
                m.popMatrix();
            }
        }
    }
}
