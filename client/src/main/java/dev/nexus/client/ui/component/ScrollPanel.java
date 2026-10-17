package dev.nexus.client.ui.component;

import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.Scissor;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertically scrolling container with smooth scrolling and a draggable scrollbar.
 * The default layout stacks children; subclasses may override {@link #layoutChildren}.
 */
public class ScrollPanel extends Component {
    protected static final float SCROLLBAR = 3;
    protected final List<Component> children = new ArrayList<>();
    protected final SmoothValue scroll = new SmoothValue(0f, 16f);
    protected float target;
    protected float contentHeight;
    protected float gap = 4;
    protected float padding = 2;
    private boolean draggingBar;
    private double dragStartY;
    private float dragStartScroll;
    private final SmoothValue barHover = new SmoothValue(0f, 16f);

    public ScrollPanel gap(float gap) { this.gap = gap; return this; }
    public ScrollPanel padding(float padding) { this.padding = padding; return this; }

    public List<Component> children() { return children; }

    public void clear() {
        children.clear();
    }

    public void add(Component c) {
        children.add(c);
    }

    public void scrollToTop() {
        target = 0;
        scroll.snap(0);
    }

    /** Positions children relative to the content origin and returns the content height. */
    protected float layoutChildren(float originX, float originY, float contentWidth) {
        float cy = 0;
        for (Component c : children) {
            float h = c.preferredHeight(contentWidth);
            c.setBounds(originX, originY + cy, contentWidth, h);
            if (h > 0.01f) cy += h + gap;
        }
        return Math.max(0, cy - gap);
    }

    protected float maxScroll() {
        return Math.max(0, contentHeight + padding * 2 - height);
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float s = scroll.get();
        float cw = width - SCROLLBAR - 5;
        contentHeight = layoutChildren(x, y + padding - s, cw);
        target = MathUtil.clamp(target, 0, maxScroll());
        float ns = scroll.update(target);
        if (Math.abs(ns - s) > 0.001f) contentHeight = layoutChildren(x, y + padding - ns, cw);

        Scissor.push(ctx, x - 2, y, width + 4, height);
        UiContext.pushClip(x - 2, y, width + 4, height);
        for (Component c : children) {
            if (c.getHeight() < 0.01f) continue;
            if (c.getY() + c.getHeight() < y - 4 || c.getY() > y + height + 4) continue;
            c.render(ctx, mx, my);
        }
        UiContext.popClip();
        Scissor.pop(ctx);

        // edge fades hint that more content exists
        if (ns > 1) Render2D.gradientV(ctx, x, y, cw, 8, 0x30000000, 0x00000000);
        if (ns < maxScroll() - 1) Render2D.gradientV(ctx, x, y + height - 8, cw, 8, 0x00000000, 0x30000000);

        float max = maxScroll();
        if (max > 0) {
            float trackH = height;
            float thumbH = Math.max(18, trackH * height / (contentHeight + padding * 2));
            float thumbY = y + (trackH - thumbH) * (ns / max);
            float bx = x + width - SCROLLBAR;
            float bh = barHover.update(draggingBar || MathUtil.inside(mx, my, bx - 3, y, SCROLLBAR + 6, height) ? 1f : 0f);
            Render2D.roundedRect(ctx, bx, y, SCROLLBAR, trackH, SCROLLBAR / 2f, 0x14FFFFFF);
            Render2D.roundedRect(ctx, bx, thumbY, SCROLLBAR, thumbH, SCROLLBAR / 2f,
                    ColorUtil.lerp(0x40FFFFFF, Theme.accent(), bh));
        }
    }

    private boolean inside(double mx, double my) {
        return MathUtil.inside(mx, my, x - 2, y, width + 4, height) && UiContext.inClip(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) {
            // still give focused/listening children a chance to release focus
            for (Component c : children) if (UiContext.isFocused(c)) c.mouseClicked(mx, my, button);
            return false;
        }
        float bx = x + width - SCROLLBAR;
        if (maxScroll() > 0 && button == 0 && mx >= bx - 3) {
            draggingBar = true;
            dragStartY = my;
            dragStartScroll = target;
            UiContext.startDrag(this);
            return true;
        }
        UiContext.pushClip(x - 2, y, width + 4, height);
        try {
            boolean handled = false;
            for (int i = children.size() - 1; i >= 0; i--) {
                Component c = children.get(i);
                if (c.getHeight() < 0.01f) continue;
                if (c.mouseClicked(mx, my, button)) {
                    handled = true;
                    break;
                }
            }
            return handled;
        } finally {
            UiContext.popClip();
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (!draggingBar) return false;
        float thumbH = Math.max(18, height * height / (contentHeight + padding * 2));
        float range = height - thumbH;
        if (range > 0) target = MathUtil.clamp(dragStartScroll + (float) ((my - dragStartY) / range) * maxScroll(), 0, maxScroll());
        scroll.snap(target);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingBar) {
            draggingBar = false;
            UiContext.stopDrag();
            return true;
        }
        boolean handled = false;
        for (Component c : children) handled |= c.mouseReleased(mx, my, button);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!inside(mx, my)) return false;
        UiContext.pushClip(x - 2, y, width + 4, height);
        try {
            for (Component c : children) if (c.getHeight() > 0.01f && c.mouseScrolled(mx, my, amount)) return true;
        } finally {
            UiContext.popClip();
        }
        if (maxScroll() <= 0) return false;
        target = MathUtil.clamp(target - (float) amount * 32f, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        for (Component c : children) if (c.keyPressed(key, scancode, modifiers)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (Component c : children) if (c.charTyped(chr, modifiers)) return true;
        return false;
    }
}
