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
import net.minecraft.client.gui.Click;
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
            dev.nexus.client.ui.vanilla.SkyTitle.INSTANCE.open();
        }
    }

    @Unique
    private void nexus$layout() {
        dev.nexus.client.ui.vanilla.SkyTitle sky = dev.nexus.client.ui.vanilla.SkyTitle.INSTANCE;
        ClickableWidget single = null, multi = null, options = null, quit = null;
        for (Element e : this.children()) {
            if (!(e instanceof ClickableWidget w) || w == this.nexus$button) continue;
            String key = nexus$key(w);
            switch (key) {
                case "menu.singleplayer": case "menu.playdemo": single = w; break;
                case "menu.multiplayer": multi = w; break;
                case "menu.options": options = w; break;
                case "menu.quit": quit = w; break;
                default: break;
            }
            // Sky DLC has a custom layout: hide every vanilla/modded button, cards + pills own the clicks
            w.visible = false;
            w.setPosition(-9999, -9999);
        }
        if (this.nexus$button != null) {
            this.nexus$button.visible = false;
            this.nexus$button.setPosition(-9999, -9999);
        }
        sky.bind(single, multi, options, quit,
                () -> this.client.setScreen(new NexusScreen()));
        sky.layout(this.width, this.height);
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

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (dev.nexus.client.ui.vanilla.SkyTitle.INSTANCE.click(click)) return true;
        return super.mouseClicked(click, doubled);
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
            dev.nexus.client.ui.vanilla.SkyTitle.INSTANCE.draw(ctx, this.client, this.width, this.height, mouseX, mouseY);

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
