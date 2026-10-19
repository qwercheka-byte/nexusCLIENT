package dev.nexus.client.ui.page;

import dev.nexus.client.render.Icon;
import dev.nexus.client.ui.NexusScreen;
import net.minecraft.client.gui.DrawContext;

/** One section of the main menu. Coordinates are virtual GUI units inside the content area. */
public abstract class Page {
    protected final NexusScreen screen;
    protected float x, y, width, height;

    protected Page(NexusScreen screen) {
        this.screen = screen;
    }

    public abstract String title();
    public abstract String subtitle();
    public abstract Icon icon();

    /** Called on every (re)layout, including window resizes. */
    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void render(DrawContext ctx, double mx, double my);

    /** Called when the page becomes visible. */
    public void onShow() {}

    /** Handle Esc for nested views; return true if consumed. */
    public boolean back() { return false; }

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    public boolean mouseScrolled(double mx, double my, double amount) { return false; }
    public boolean keyPressed(int key, int scancode, int modifiers) { return false; }
    public boolean charTyped(char chr, int modifiers) { return false; }
}
