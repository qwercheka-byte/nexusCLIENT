package dev.nexus.client.module.impl.hud;

import dev.nexus.client.event.events.MouseButtonEvent;
import dev.nexus.client.hud.HudModule;
import dev.nexus.client.hud.HudStyle;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ColorSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Shows movement keys, jump and mouse buttons with press animation and clicks per second. */
public final class KeystrokesModule extends HudModule {
    private static final int W = 0, A = 1, S = 2, D = 3, JUMP = 4, LMB = 5, RMB = 6;

    private final BooleanSetting mouse = add(new BooleanSetting("mouse", "Mouse buttons", "Show LMB / RMB", true));
    private final BooleanSetting cps = add(new BooleanSetting("cps", "CPS", "Show clicks per second on the mouse keys", true))
            .visibleWhen(mouse::isOn);
    private final BooleanSetting space = add(new BooleanSetting("space", "Space bar", "Show the jump key", true));
    private final NumberSetting keySize = add(new NumberSetting("key_size", "Key size", "Size of a key", 20, 14, 32, 1).suffix("px"));
    private final NumberSetting gap = add(new NumberSetting("gap", "Spacing", "Space between keys", 2, 0, 6, 0.5).suffix("px"));
    private final ModeSetting pressColorMode = add(new ModeSetting("press_color_mode", "Pressed color",
            "Colour of pressed keys", "Accent", "Accent", "Custom"));
    private final ColorSetting pressColor = add(new ColorSetting("press_color", "Custom pressed color",
            "Custom colour of pressed keys", 0xFFFFFFFF, false)).visibleWhen(() -> pressColorMode.is("Custom"));

    private final SmoothValue[] press = new SmoothValue[7];
    private final String[] labels = {"W", "A", "S", "D", "", "LMB", "RMB"};
    private final String[] boundKeys = new String[4];
    private final long[] leftClicks = new long[64], rightClicks = new long[64];
    private int leftIndex, rightIndex;
    private int cachedLeftCps = -1, cachedRightCps = -1;
    private String leftCpsText = "0 CPS", rightCpsText = "0 CPS";

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Displays WASD, jump and mouse presses with clicks per second.",
                false, START, END, 4, 4);
        for (int i = 0; i < press.length; i++) press[i] = new SmoothValue(0f, 22f);
        on(MouseButtonEvent.class, e -> {
            if (!e.isPress() || mc.currentScreen != null) return;
            long now = System.currentTimeMillis();
            if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) leftClicks[leftIndex++ & 63] = now;
            else if (e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightClicks[rightIndex++ & 63] = now;
        });
    }

    private static int countRecent(long[] clicks, long now) {
        int n = 0;
        for (long t : clicks) if (now - t <= 1000) n++;
        return n;
    }

    private void refreshLabels() {
        KeyBinding[] keys = {mc.options.forwardKey, mc.options.leftKey, mc.options.backKey, mc.options.rightKey};
        for (int i = 0; i < 4; i++) {
            String tk = keys[i].getBoundKeyTranslationKey();
            if (!tk.equals(boundKeys[i])) {
                boundKeys[i] = tk;
                String name = keys[i].getBoundKeyLocalizedText().getString();
                labels[i] = name.length() <= 3 ? name.toUpperCase(Locale.ROOT) : name.substring(0, 3);
            }
        }
    }

    @Override
    public boolean render(DrawContext ctx, boolean editor) {
        if (mc.options == null) return false;
        refreshLabels();
        boolean inGame = mc.currentScreen == null || editor;
        press[W].update(inGame && mc.options.forwardKey.isPressed() ? 1 : 0);
        press[A].update(inGame && mc.options.leftKey.isPressed() ? 1 : 0);
        press[S].update(inGame && mc.options.backKey.isPressed() ? 1 : 0);
        press[D].update(inGame && mc.options.rightKey.isPressed() ? 1 : 0);
        press[JUMP].update(inGame && mc.options.jumpKey.isPressed() ? 1 : 0);
        press[LMB].update(inGame && mc.options.attackKey.isPressed() ? 1 : 0);
        press[RMB].update(inGame && mc.options.useKey.isPressed() ? 1 : 0);

        float k = keySize.getFloat(), g = gap.getFloat();
        float total = k * 3 + g * 2;
        float y = 0;
        key(ctx, W, k + g, y, k, k, labels[W], null);
        y += k + g;
        key(ctx, A, 0, y, k, k, labels[A], null);
        key(ctx, S, k + g, y, k, k, labels[S], null);
        key(ctx, D, (k + g) * 2, y, k, k, labels[D], null);
        y += k + g;
        if (mouse.isOn()) {
            long now = System.currentTimeMillis();
            int l = countRecent(leftClicks, now), r = countRecent(rightClicks, now);
            if (l != cachedLeftCps) { cachedLeftCps = l; leftCpsText = l + " CPS"; }
            if (r != cachedRightCps) { cachedRightCps = r; rightCpsText = r + " CPS"; }
            float mw = (total - g) / 2f;
            key(ctx, LMB, 0, y, mw, k, labels[LMB], cps.isOn() ? leftCpsText : null);
            key(ctx, RMB, mw + g, y, mw, k, labels[RMB], cps.isOn() ? rightCpsText : null);
            y += k + g;
        }
        if (space.isOn()) {
            float sh = Math.max(8, k * 0.55f);
            key(ctx, JUMP, 0, y, total, sh, null, null);
            y += sh + g;
        }
        setSize(total, y - g);
        return true;
    }

    private void key(DrawContext ctx, int index, float x, float y, float w, float h, String label, String sub) {
        float p = press[index].get();
        int accent = pressColorMode.is("Custom") ? pressColor.get() : Theme.accent();
        float r = HudStyle.radius();
        int bg = hasBackground() ? HudStyle.PANEL : 0x00000000;
        Render2D.roundedRect(ctx, x, y, w, h, r, ColorUtil.lerp(bg, ColorUtil.withAlpha(accent, 0xE6), p));
        if (!hasBackground() && p < 0.99f) Render2D.roundedOutline(ctx, x, y, w, h, r, 1f, 0x60FFFFFF);
        int luminance = (ColorUtil.red(accent) * 299 + ColorUtil.green(accent) * 587 + ColorUtil.blue(accent) * 114) / 1000;
        int pressedText = luminance > 170 ? 0xFF111217 : 0xFFFFFFFF;
        int textColor = ColorUtil.lerp(HudStyle.VALUE, pressedText, p);
        if (label == null) {
            // space bar: a small line instead of text
            float lw = Math.min(w * 0.35f, 30f);
            Render2D.rect(ctx, x + (w - lw) / 2f, y + h / 2f - 0.5f, lw, 1f, textColor);
            return;
        }
        if (sub != null) {
            HudStyle.text(ctx, label, x + (w - TextRender.width(label)) / 2f, y + h / 2f - 8, textColor);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x + w / 2f, y + h / 2f + 1.5f);
            ctx.getMatrices().scale(0.6f, 0.6f);
            TextRender.draw(ctx, sub, -TextRender.width(sub) / 2f, 0, ColorUtil.lerp(HudStyle.LABEL, pressedText, p), false);
            ctx.getMatrices().popMatrix();
        } else {
            HudStyle.text(ctx, label, x + (w - TextRender.width(label)) / 2f, y + (h - 8) / 2f, textColor);
        }
    }
}
