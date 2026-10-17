package dev.nexus.client.ui.component;

import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Slider for a {@link NumberSetting}: drag, click, mouse wheel and arrow keys (when hovered). */
public class Slider extends Component {
    private static final float VALUE_WIDTH = 40;
    private final NumberSetting setting;
    private final SmoothValue fill;
    private final SmoothValue grab = new SmoothValue(0f, 20f);
    private boolean dragging;
    private boolean hoveredLast;

    public Slider(NumberSetting setting) {
        this.setting = setting;
        this.fill = new SmoothValue((float) setting.getProgress(), 22f);
        this.height = 14;
    }

    private float trackX() { return x; }
    private float trackW() { return width - VALUE_WIDTH - 6; }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float h = updateHover(mx, my);
        hoveredLast = isHovered(mx, my);
        float g = grab.update(dragging ? 1f : 0f);
        float p = fill.update((float) setting.getProgress());
        float tx = trackX(), tw = trackW(), cy = y + height / 2f;
        float th = 3f + h * 1f;
        Render2D.roundedRect(ctx, tx, cy - th / 2f, tw, th, th / 2f, 0xFF2A2B36);
        Render2D.roundedGradient(ctx, tx, cy - th / 2f, Math.max(th, tw * p), th, th / 2f, Theme.accentDim(), Theme.accent(), true);
        float knobR = 3.6f + h * 0.8f + g * 0.6f;
        float kx = tx + tw * p;
        if (g > 0.01f) Render2D.circle(ctx, kx, cy, knobR + 3f * g, ColorUtil.withAlpha(Theme.accent(), (int) (60 * g)));
        Render2D.circle(ctx, kx, cy + 0.4f, knobR + 0.4f, 0x50000000);
        Render2D.circle(ctx, kx, cy, knobR, 0xFFFFFFFF);
        // value pill
        float vx = x + width - VALUE_WIDTH;
        Render2D.roundedRect(ctx, vx, y, VALUE_WIDTH, height, Theme.RADIUS_SMALL, ColorUtil.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, h));
        String v = setting.format();
        TextRender.drawCentered(ctx, TextRender.ellipsize(v, (int) VALUE_WIDTH - 4), vx + VALUE_WIDTH / 2f, TextRender.centerY(y, height), Theme.TEXT);
    }

    private void setFromMouse(double mx) {
        double progress = (mx - trackX()) / trackW();
        setting.setProgress(progress);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isHovered(mx, my)) return false;
        if (mx > trackX() + trackW() + 3) {
            // clicking the value pill steps forward (shift: backwards)
            step(dev.nexus.client.util.Mc.shiftDown() ? -1 : 1);
            return true;
        }
        dragging = true;
        UiContext.startDrag(this);
        setFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging) return false;
        setFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging) return false;
        dragging = false;
        UiContext.stopDrag();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isHovered(mx, my) || !dev.nexus.client.util.Mc.shiftDown()) return false; // plain wheel scrolls the page
        step(amount > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (!hoveredLast) return false;
        if (key == GLFW.GLFW_KEY_LEFT) { step(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { step(1); return true; }
        return false;
    }

    private void step(int dir) {
        setting.set(setting.get() + setting.getStep() * dir);
    }
}
