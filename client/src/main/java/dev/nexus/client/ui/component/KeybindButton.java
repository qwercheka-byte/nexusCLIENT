package dev.nexus.client.ui.component;

import dev.nexus.client.ui.animation.AnimationClock;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.KeybindSetting;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * Click to listen, then press a key or mouse button (middle / side buttons).
 * Esc cancels, Backspace / Delete clears the binding.
 */
public class KeybindButton extends Component {
    private final KeybindSetting setting;

    public KeybindButton(KeybindSetting setting) {
        this.setting = setting;
        this.height = 16;
    }

    public boolean isListening() {
        return UiContext.isFocused(this);
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float h = updateHover(mx, my);
        boolean listening = isListening();
        int bg = listening ? Theme.accentSoft() : ColorUtil.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, h);
        Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS_SMALL, bg);
        Render2D.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_SMALL, 1f, listening ? Theme.accent() : Theme.BORDER);
        String text;
        int color;
        if (listening) {
            float pulse = 0.2f + 0.8f * AnimationClock.wave(1130);
            text = "Press a key…";
            color = ColorUtil.mulAlpha(Theme.TEXT, pulse);
        } else {
            text = setting.isBound() ? setting.keyName() : "None";
            color = setting.isBound() ? Theme.TEXT : Theme.TEXT_DIM;
        }
        IconRender.draw(ctx, Icon.KEYBOARD, x + 5, y + (height - 9) / 2f, 9, listening ? Theme.accentBright() : Theme.TEXT_DIM);
        TextRender.drawCentered(ctx, TextRender.ellipsize(text, (int) width - 20), x + 8 + width / 2f, TextRender.centerY(y, height), color);
        if (isHovered(mx, my) && !listening) UiContext.tooltip("Click to bind · Backspace clears", mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (isListening()) {
            // middle / side mouse buttons can be bound; left/right click cancels
            if (button >= GLFW.GLFW_MOUSE_BUTTON_MIDDLE) setting.set(KeybindSetting.MOUSE_BASE + button);
            UiContext.unfocus(this);
            return true;
        }
        if (button != 0 || !isHovered(mx, my)) return false;
        UiContext.focus(this);
        Button.playClick();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (!isListening()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            UiContext.unfocus(this);
        } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            setting.set(KeybindSetting.NONE);
            UiContext.unfocus(this);
        } else {
            setting.set(key);
            UiContext.unfocus(this);
        }
        return true;
    }
}
