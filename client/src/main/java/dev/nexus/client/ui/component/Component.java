package dev.nexus.client.ui.component;

import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

/** Base class of all NEXUS widgets. Coordinates are in (virtual) GUI units. */
public abstract class Component {
    protected float x, y, width, height;
    protected final SmoothValue hover = new SmoothValue(0f, 18f);
    protected boolean enabled = true;

    public Component setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public abstract void render(DrawContext ctx, double mouseX, double mouseY);

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    /** Called for the component registered via {@link UiContext#startDrag}. */
    public boolean mouseDragged(double mx, double my, int button) { return false; }
    public boolean mouseScrolled(double mx, double my, double amount) { return false; }
    public boolean keyPressed(int key, int scancode, int modifiers) { return false; }
    public boolean charTyped(char chr, int modifiers) { return false; }

    /** Height the component wants for a given width (used by vertical layouts). */
    public float preferredHeight(float width) {
        return height;
    }

    public boolean isHovered(double mx, double my) {
        return enabled && MathUtil.inside(mx, my, x, y, width, height) && UiContext.inClip(mx, my);
    }

    protected float updateHover(double mx, double my) {
        return hover.update(isHovered(mx, my) ? 1f : 0f);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
}
