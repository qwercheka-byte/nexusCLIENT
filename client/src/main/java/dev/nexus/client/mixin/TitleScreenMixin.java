package dev.nexus.client.mixin;

import dev.nexus.client.ui.NexusScreen;
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

    @Inject(method = "init()V", at = @At("TAIL"))
    private void nexus$layout(CallbackInfo ci) {
        if (this.client != null) {
            this.nexus$decor.initSkin(this.client);
            List<ClickableWidget> column = new ArrayList<>();
            List<ClickableWidget> icons = new ArrayList<>();
            ClickableWidget options = null;
            ClickableWidget quit = null;
            ClickableWidget primary = null;
            this.nexus$realms = null;

            for (Element e : this.children()) {
                if (e instanceof ClickableWidget w) {
                    String key = nexus$key(w);
                    switch (key) {
                        case "menu.singleplayer":
                        case "menu.playdemo":
                            column.add(w);
                            primary = w;
                            break;
                        case "menu.multiplayer":
                        case "menu.resetdemo":
                        case "modmenu.title":
                            column.add(w);
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
                            if (w instanceof TextIconButtonWidget) {
                                icons.add(w);
                            }
                    }
                }
            }

            column.sort(Comparator.comparingInt(ClickableWidget::getY));
            ButtonWidget nexus = ButtonWidget.builder(Text.translatable("nexus.title.open"), b -> this.client.setScreen(new NexusScreen())).build();
            NexusWidgets.setStyle(nexus, NexusWidgets.Style.BRAND);
            this.addDrawableChild(nexus);
            column.add(nexus);
            if (primary != null) {
                NexusWidgets.setStyle(primary, NexusWidgets.Style.PRIMARY);
            }

            int bw = 200;
            int bh = 20;
            int step = 24;
            int buttonsH = column.size() * step + 4 + bh;
            float logoH = this.height >= 300 ? 96.0F : (this.height >= 230 ? 70.0F : 44.0F);
            boolean greet = this.height >= 250;
            float greetH = greet ? 24.0F : 0.0F;
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

            y += 4;
            if (options != null) {
                options.setDimensionsAndPosition(bw / 2 - 2, bh, x, y);
                this.nexus$track(options, y);
            }

            if (quit != null) {
                quit.setDimensionsAndPosition(bw / 2 - 2, bh, x + bw / 2 + 2, y);
                this.nexus$track(quit, y);
            }

            if (icons.size() >= 1) {
                icons.get(0).setPosition(x - 24, y);
                this.nexus$track(icons.get(0), y);
            }

            if (icons.size() >= 2) {
                icons.get(1).setPosition(x + bw + 4, y);
                this.nexus$track(icons.get(1), y);
            }
        }
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
            this.renderPanoramaBackground(ctx, delta);
            this.applyBlur(ctx);
            this.nexus$decor.overlay(ctx, this.width, this.height);
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
                d.render(ctx, mouseX, mouseY, delta);
            }

            this.nexus$decor.footer(ctx, this.client, this.height);
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
