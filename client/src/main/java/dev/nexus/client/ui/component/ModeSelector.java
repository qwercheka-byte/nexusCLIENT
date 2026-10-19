package dev.nexus.client.ui.component;

import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.Scissor;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.ModeSetting;
import dev.nexus.client.ui.animation.Animation;
import dev.nexus.client.ui.animation.Easing;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** "‹ Mode ›" selector: click the left/right half or scroll (with Shift) to cycle; the label slides. */
public class ModeSelector extends Component {
    private final ModeSetting setting;
    private final Animation slide = new Animation(1f, 200, Easing.OUT_CUBIC);
    private String previous = "";
    private int direction = 1;
    private String last;

    public ModeSelector(ModeSetting setting) {
        this.setting = setting;
        this.last = setting.get();
        this.height = 16;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float h = updateHover(mx, my);
        if (!setting.get().equals(last)) {
            previous = last;
            last = setting.get();
            slide.restart(0f, 1f);
        }
        Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS_SMALL, ColorUtil.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, h));
        Render2D.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_SMALL, 1f, Theme.BORDER);
        boolean leftHalf = mx < x + width / 2f;
        int arrowL = isHovered(mx, my) && leftHalf ? Theme.TEXT : Theme.TEXT_DIM;
        int arrowR = isHovered(mx, my) && !leftHalf ? Theme.TEXT : Theme.TEXT_DIM;
        // chevron-right rotated for the left arrow
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + 9, y + height / 2f);
        ctx.getMatrices().rotate((float) Math.PI);
        IconRender.drawCentered(ctx, Icon.CHEVRON_RIGHT, 0, 0, 8, arrowL);
        ctx.getMatrices().popMatrix();
        IconRender.drawCentered(ctx, Icon.CHEVRON_RIGHT, x + width - 9, y + height / 2f, 8, arrowR);

        float t = slide.get();
        float cx = x + width / 2f, ty = TextRender.centerY(y, height);
        int maxW = (int) width - 30;
        Scissor.push(ctx, x + 15, y, width - 30, height);
        if (t < 1f && !previous.isEmpty()) {
            Render2D.pushAlpha(1f - t);
            TextRender.drawCentered(ctx, TextRender.ellipsize(previous, maxW), cx - direction * 14 * t, ty, Theme.TEXT);
            Render2D.popAlpha();
        }
        Render2D.pushAlpha(t);
        TextRender.drawCentered(ctx, TextRender.ellipsize(setting.get(), maxW), cx + direction * 14 * (1f - t), ty, Theme.TEXT);
        Render2D.popAlpha();
        Scissor.pop(ctx);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isHovered(mx, my) || (button != 0 && button != 1)) return false;
        direction = (mx < x + width / 2f || button == 1) ? -1 : 1;
        setting.cycle(direction);
        Button.playClick();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isHovered(mx, my) || !dev.nexus.client.util.Mc.shiftDown()) return false;
        direction = amount > 0 ? -1 : 1;
        setting.cycle(direction);
        return true;
    }
}
