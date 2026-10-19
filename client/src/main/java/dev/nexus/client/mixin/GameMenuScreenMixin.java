package dev.nexus.client.mixin;

import dev.nexus.client.Nexus;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.ui.vanilla.NexusWidgets;
import dev.nexus.client.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    @Shadow
    @Final
    private boolean showMenu;
    @Unique
    private static final Text NEXUS_LOGO = Text.literal("NEXUS").setStyle(Style.EMPTY.withBold(true));
    @Unique
    private float nexus$x;
    @Unique
    private float nexus$y;
    @Unique
    private float nexus$w;
    @Unique
    private float nexus$h;
    @Unique
    private boolean nexus$panel;
    @Unique
    private final long nexus$openedAt = Util.getMeasuringTimeMs();

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void nexus$init(CallbackInfo ci) {
        this.nexus$panel = false;
        if (this.showMenu && this.client != null) {
            List<ClickableWidget> buttons = new ArrayList<>();
            ClickableWidget back = null;

            for (Element e : new ArrayList<>(this.children())) {
                if (e instanceof TextWidget tw && tw.getMessage().equals(this.title)) {
                    this.remove(tw);
                } else if (e instanceof ClickableWidget w) {
                    buttons.add(w);
                    if (w.getMessage().getContent() instanceof TranslatableTextContent t && t.getKey().equals("menu.returnToGame")) {
                        back = w;
                    }
                }
            }

            if (back != null && !buttons.isEmpty()) {
                NexusWidgets.setStyle(back, NexusWidgets.Style.PRIMARY);
                int insertY = back.getY() + 24;

                for (ClickableWidget wx : buttons) {
                    if (wx.getY() >= insertY) {
                        wx.setY(wx.getY() + 24);
                    }
                }

                ButtonWidget nexus = ButtonWidget.builder(Text.translatable("nexus.title.open"), b -> this.client.setScreen(new NexusScreen()))
                    .dimensions(back.getX(), insertY, back.getWidth(), 20)
                    .build();
                NexusWidgets.setStyle(nexus, NexusWidgets.Style.BRAND);
                this.addDrawableChild(nexus);
                buttons.add(nexus);
                int minX = Integer.MAX_VALUE;
                int minY = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int maxY = Integer.MIN_VALUE;

                for (ClickableWidget wxx : buttons) {
                    minX = Math.min(minX, wxx.getX());
                    minY = Math.min(minY, wxx.getY());
                    maxX = Math.max(maxX, wxx.getRight());
                    maxY = Math.max(maxY, wxx.getBottom());
                }

                int header = 34;
                int footer = 26;
                int pad = 10;
                int needTop = minY - header - pad;
                if (needTop < 4) {
                    int shift = 4 - needTop;

                    for (ClickableWidget wxx : buttons) {
                        wxx.setY(wxx.getY() + shift);
                    }

                    minY += shift;
                    maxY += shift;
                }

                this.nexus$x = minX - pad;
                this.nexus$y = minY - header - pad + 4;
                this.nexus$w = maxX - minX + pad * 2;
                this.nexus$h = maxY - minY + header + footer + pad;
                this.nexus$panel = this.nexus$y + this.nexus$h <= this.height;
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void nexus$panel(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.nexus$panel && this.client != null) {
            float t = Math.min(1.0F, (float)(Util.getMeasuringTimeMs() - this.nexus$openedAt) / 260.0F);
            t = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            Render2D.pushAlpha(t);
            float x = this.nexus$x;
            float y = this.nexus$y + (1.0F - t) * 6.0F;
            float w = this.nexus$w;
            float h = this.nexus$h;
            float r = 8.0F;
            Render2D.shadow(ctx, x, y + 4.0F, w, h, r, 16.0F, -1879048192);
            Render2D.roundedRect(ctx, x, y, w, h, r, -535817962);
            Render2D.roundedOutline(ctx, x, y, w, h, r, 1.0F, 536870911);
            Render2D.gradientH(ctx, x + 12.0F, y, w / 2.0F - 12.0F, 1.0F, ColorUtil.withAlpha(Theme.accent(), 0), Theme.accent());
            Render2D.gradientH(ctx, x + w / 2.0F, y, w / 2.0F - 12.0F, 1.0F, Theme.accent(), ColorUtil.withAlpha(Theme.accent(), 0));
            float tx = x + 10.0F;
            float ty = y + 9.0F;
            Render2D.roundedGradient(ctx, tx, ty, 18.0F, 18.0F, 5.0F, Theme.accent(), ColorUtil.lerp(Theme.accent(), -13058568, 0.55F), true);
            IconRender.drawCentered(ctx, Icon.LOGO, tx + 9.0F, ty + 9.0F, 12.0F, Theme.onAccent());
            TextRender.draw(ctx, NEXUS_LOGO, tx + 25.0F, ty + 1.0F, -1249805, false);
            TextRender.drawScaled(ctx, "CLIENT " + Nexus.VERSION, tx + 25.0F, ty + 11.0F, 0.7F, -10460550, false);
            int tw = TextRender.width(this.title);
            TextRender.draw(ctx, this.title, x + w - 10.0F - tw, ty + 5.0F, -6644563, false);
            Render2D.rect(ctx, x + 10.0F, y + 34.0F, w - 20.0F, 1.0F, 352321535);
            float fy = y + h - 20.0F;
            Render2D.rect(ctx, x + 10.0F, fy - 5.0F, w - 20.0F, 1.0F, 352321535);
            if (this.client.player != null) {
                PlayerListEntry entry = this.client.getNetworkHandler() != null
                    ? this.client.getNetworkHandler().getPlayerListEntry(this.client.player.getUuid())
                    : null;
                SkinTextures skin = entry != null ? entry.getSkinTextures() : DefaultSkinHelper.getSkinTextures(this.client.player.getGameProfile());
                PlayerSkinDrawer.draw(ctx, skin, Math.round(x + 10.0F), Math.round(fy), 10);
                String name = this.client.player.getName().getString();
                TextRender.draw(ctx, name, x + 25.0F, fy + 1.0F, -1249805, false);
                ServerInfo server = this.client.getCurrentServerEntry();
                String place = this.client.isInSingleplayer()
                    ? Text.translatable("menu.singleplayer").getString()
                    : (server != null ? server.name : Text.translatable("menu.multiplayer").getString());
                String fpsText = this.client.getCurrentFps() + " FPS";
                int room = (int)(w - 20.0F - 19.0F - TextRender.width(name) - 6.0F - TextRender.width(fpsText) - 8.0F);
                TextRender.draw(ctx, TextRender.ellipsize("·  " + place, room), x + 29.0F + TextRender.width(name), fy + 1.0F, -10460550, false);
            }

            String fps = this.client.getCurrentFps() + " FPS";
            TextRender.draw(ctx, fps, x + w - 10.0F - TextRender.width(fps), fy + 1.0F, Theme.accentBright(), false);
            Render2D.popAlpha();
        }
    }
}
