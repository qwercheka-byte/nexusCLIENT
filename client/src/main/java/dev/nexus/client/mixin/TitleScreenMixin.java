package dev.nexus.client.mixin;

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
            if (w instanceof PressableTextWidget) continue; // copyright line keeps its vanilla place
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
                    } else if (w instanceof ButtonWidget) {
                        // multiplayer, demo reset, Mod Menu's "Mods" and buttons of other mods
                        column.add(w);
                    }
            }
        }

        column.sort(Comparator.comparingInt(ClickableWidget::getY).thenComparingInt(ClickableWidget::getX));
        icons.sort(Comparator.comparingInt(ClickableWidget::getX));
        if (this.nexus$button != null) column.add(this.nexus$button);
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
        if (logoH + greetH + buttonsH > this.height - 12) {
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

        y += 4;
        if (options != null) {
            options.setDimensionsAndPosition(bw / 2 - 2, bh, x, y);
            this.nexus$track(options, y);
        }

        if (quit != null) {
            quit.setDimensionsAndPosition(bw / 2 - 2, bh, x + bw / 2 + 2, y);
            this.nexus$track(quit, y);
        }

        // vanilla has two icon buttons (language left, accessibility right); extra ones continue outwards
        for (int i = 0; i < icons.size(); i++) {
            ClickableWidget icon = icons.get(i);
            int n = i / 2;
            int ix = (i & 1) == 0 ? x - 24 - n * 24 : x + bw + 4 + n * 24;
            icon.setPosition(ix, y);
            this.nexus$track(icon, y);
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
