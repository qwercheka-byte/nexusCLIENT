package dev.nexus.client.ui.component;

import dev.nexus.client.render.Render2D;
import dev.nexus.client.ui.animation.ToggleAnimation;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Animated on/off switch bound to external state. */
public class Toggle extends Component {
    public static final float WIDTH = 24, HEIGHT = 13;

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final ToggleAnimation knob;

    public Toggle(BooleanSupplier getter, Consumer<Boolean> setter) {
        this.getter = getter;
        this.setter = setter;
        this.knob = ToggleAnimation.forSwitch(getter.getAsBoolean());
        this.width = WIDTH;
        this.height = HEIGHT;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float h = updateHover(mx, my);
        float t = knob.update(getter.getAsBoolean());
        float tc = knob.clamped();
        int track = ColorUtil.lerp(ColorUtil.lerp(0xFF2A2B36, 0xFF343544, h), Theme.accent(), tc);
        Render2D.roundedRect(ctx, x, y, width, height, height / 2f, track);
        float pad = 2f, size = height - pad * 2;
        float kx = x + pad + (width - pad * 2 - size) * t;
        Render2D.circle(ctx, kx + size / 2f, y + height / 2f + 0.4f, size / 2f + 0.3f, 0x40000000);
        Render2D.circle(ctx, kx + size / 2f, y + height / 2f, size / 2f, ColorUtil.lerp(0xFFB9BBC9, 0xFFFFFFFF, tc));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isHovered(mx, my)) return false;
        setter.accept(!getter.getAsBoolean());
        Button.playClick();
        return true;
    }
}
