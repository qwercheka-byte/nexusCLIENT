package dev.nexus.client.ui.component;

import dev.nexus.client.util.Lang;
import dev.nexus.client.Nexus;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.ColorSetting;
import dev.nexus.client.setting.KeybindSetting;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.setting.MultiSelectSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.setting.Setting;
import dev.nexus.client.setting.StringSetting;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * One setting: name on the left, the matching control on the right (or below for chips / colour body).
 * Hidden settings collapse with an animation; right-click on the label resets to the default.
 */
public class SettingRow extends Component {
    private enum Layout { INLINE, OVERLAY, BLOCK }

    private static final float ROW = 26, PAD = 8;
    private final Setting<?> setting;
    private final Component control;
    private final Layout layout;
    private final float controlWidth;
    private final SmoothValue visibility;
    private final String tooltip;

    public SettingRow(Setting<?> setting) {
        this.setting = setting;
        this.visibility = new SmoothValue(setting.isVisible() ? 1f : 0f, 16f);
        this.tooltip = setting.getDescription().isEmpty()
                ? "Right-click to reset"
                : setting.getDescription() + "\n\n" + Lang.tr("Right-click to reset");
        if (setting == Nexus.settings().accentPreset && setting instanceof ModeSetting m) {
            control = new AccentSwatches(m, () -> Nexus.settings().customAccent.get());
            layout = Layout.INLINE;
            controlWidth = AccentSwatches.naturalWidth(m.getModes().size());
        } else if (setting instanceof BooleanSetting b) {
            control = new Toggle(b::isOn, b::set);
            layout = Layout.INLINE;
            controlWidth = Toggle.WIDTH;
        } else if (setting instanceof NumberSetting n) {
            control = new Slider(n);
            layout = Layout.INLINE;
            controlWidth = 140;
        } else if (setting instanceof ModeSetting m) {
            control = new ModeSelector(m);
            layout = Layout.INLINE;
            float longest = 0;
            for (String s : m.getModes()) longest = Math.max(longest, TextRender.width(s));
            controlWidth = MathUtil.clamp(longest + 36, 90, 140);
        } else if (setting instanceof KeybindSetting k) {
            control = new KeybindButton(k);
            layout = Layout.INLINE;
            controlWidth = 90;
        } else if (setting instanceof StringSetting s) {
            control = TextField.forSetting(s);
            layout = Layout.INLINE;
            controlWidth = 150;
        } else if (setting instanceof ColorSetting c) {
            control = new ColorPicker(c);
            layout = Layout.OVERLAY;
            controlWidth = 0;
        } else if (setting instanceof MultiSelectSetting ms) {
            control = new MultiSelect(ms);
            layout = Layout.BLOCK;
            controlWidth = 0;
        } else {
            throw new IllegalArgumentException("No UI control for setting type " + setting.getClass().getName());
        }
    }

    public Setting<?> setting() {
        return setting;
    }

    private float fullHeight(float w) {
        return switch (layout) {
            case INLINE -> ROW;
            case OVERLAY -> 5 + control.preferredHeight(w - PAD * 2) + 5;
            case BLOCK -> 20 + control.preferredHeight(w - PAD * 2) + 7;
        };
    }

    @Override
    public float preferredHeight(float w) {
        return fullHeight(w) * visibility.get();
    }

    private boolean labelHovered(double mx, double my) {
        return MathUtil.inside(mx, my, x, y, layout == Layout.INLINE ? width - controlWidth - PAD : width * 0.45f,
                layout == Layout.INLINE ? height : 20) && UiContext.inClip(mx, my);
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float v = visibility.update(setting.isVisible() ? 1f : 0f);
        if (v < 0.01f) return;
        float h = updateHover(mx, my);
        Render2D.pushAlpha(v);
        Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS_SMALL, ColorUtil.lerp(0x0AFFFFFF, 0x12FFFFFF, h));

        float labelY = layout == Layout.INLINE ? TextRender.centerY(y, height) : y + 8;
        float labelMax = layout == Layout.INLINE ? width - controlWidth - PAD * 3 : width * 0.5f;
        String name = TextRender.ellipsize(setting.getName(), (int) labelMax);
        TextRender.draw(ctx, name, x + PAD, labelY, Theme.TEXT);
        if (!setting.isDefault()) {
            Render2D.circle(ctx, x + PAD + TextRender.width(name) + 5, labelY + 3.5f, 1.6f, Theme.accent());
        }

        switch (layout) {
            case INLINE -> control.setBounds(x + width - PAD - controlWidth,
                    y + (height - control.getHeight()) / 2f, controlWidth, control.getHeight());
            case OVERLAY -> control.setBounds(x + PAD, y + 5, width - PAD * 2, control.preferredHeight(width - PAD * 2));
            case BLOCK -> control.setBounds(x + PAD, y + 20, width - PAD * 2, control.preferredHeight(width - PAD * 2));
        }
        control.render(ctx, mx, my);
        Render2D.popAlpha();
        if (labelHovered(mx, my)) UiContext.tooltip(tooltip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (visibility.get() < 0.6f) return false;
        if (control.mouseClicked(mx, my, button)) return true;
        if (button == 1 && labelHovered(mx, my)) {
            setting.reset();
            Button.playClick();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { return control.mouseReleased(mx, my, button); }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return visibility.get() > 0.6f && control.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return visibility.get() > 0.6f && control.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) { return control.charTyped(chr, modifiers); }
}
