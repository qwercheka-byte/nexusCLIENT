package dev.nexus.client.ui.component;

import dev.nexus.client.module.Module;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

/** Grid card for one module: icon, name, category, description, keybind chip and toggle. */
public class ModuleCard extends Component {
    public static final float HEIGHT = 64;

    private final Module module;
    private final Toggle toggle;
    private final Consumer<Module> openSettings;
    private final SmoothValue enabledAnim;
    private final SmoothValue offsetX = new SmoothValue(0f, 14f), offsetY = new SmoothValue(0f, 14f);
    private final SmoothValue appear = new SmoothValue(0f, 10f);
    private boolean placed;
    private final String categoryName;
    private String line1 = "", line2 = "";
    private float wrappedFor = -1;

    public ModuleCard(Module module, Consumer<Module> openSettings) {
        this.module = module;
        this.openSettings = openSettings;
        this.toggle = new Toggle(module::isEnabled, v -> module.toggle());
        this.enabledAnim = new SmoothValue(module.isEnabled() ? 1f : 0f, 14f);
        this.categoryName = module.getCategory().getDisplayName().toUpperCase(java.util.Locale.ROOT);
        this.height = HEIGHT;
    }

    public Module module() { return module; }

    /** Hides the card so it fades in again when it reappears in the grid. */
    public void hideInstantly() {
        placed = false;
        appear.snap(0f);
    }

    /** Positions the card; the offset (relative to the grid origin) animates when the grid reflows. */
    public void place(float originX, float originY, float relX, float relY, float w) {
        if (!placed) {
            offsetX.snap(relX);
            offsetY.snap(relY + 8);
            placed = true;
        }
        float ox = offsetX.update(relX), oy = offsetY.update(relY);
        setBounds(originX + ox, originY + oy, w, HEIGHT);
    }

    private void wrap(float maxW) {
        if (maxW == wrappedFor) return;
        wrappedFor = maxW;
        String[] words = module.getDescription().split(" ");
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        boolean second = false;
        for (String w : words) {
            StringBuilder target = second ? b : a;
            String candidate = target.isEmpty() ? w : target + " " + w;
            if (!second && TextRender.width(candidate) > maxW) {
                second = true;
                target = b;
                candidate = w;
            }
            target.setLength(0);
            target.append(candidate);
        }
        line1 = a.toString();
        line2 = TextRender.ellipsize(b.toString(), (int) maxW);
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float h = updateHover(mx, my);
        float e = enabledAnim.update(module.isEnabled() ? 1f : 0f);
        float a = appear.update(1f);
        Render2D.pushAlpha(a);
        Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, h));
        Render2D.roundedOutline(ctx, x, y, width, height, Theme.RADIUS, 1f,
                ColorUtil.lerp(ColorUtil.lerp(Theme.BORDER, Theme.BORDER_STRONG, h), ColorUtil.withAlpha(Theme.accent(), 0x70), e));
        if (e > 0.01f) {
            int c = ColorUtil.mulAlpha(Theme.accent(), e);
            Render2D.gradientH(ctx, x + 8, y, (width - 16) / 2f, 1, ColorUtil.withAlpha(c, 0), c);
            Render2D.gradientH(ctx, x + width / 2f, y, (width - 16) / 2f, 1, c, ColorUtil.withAlpha(c, 0));
        }

        // icon tile
        float ix = x + 9, iy = y + 9;
        Render2D.roundedRect(ctx, ix, iy, 20, 20, 5, ColorUtil.lerp(0x12FFFFFF, Theme.accentSoft(), e));
        IconRender.drawCentered(ctx, module.getCategory().getIcon(), ix + 10, iy + 10, 11,
                ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accentBright(), e));

        // toggle and keybind chip
        toggle.setBounds(x + width - 9 - Toggle.WIDTH, y + 12, Toggle.WIDTH, Toggle.HEIGHT);
        toggle.render(ctx, mx, my);
        float right = x + width - 9 - Toggle.WIDTH - 6;
        if (module.getKeybind().isBound()) {
            String key = TextRender.ellipsize(module.getKeybind().keyName(), 40);
            float kw = TextRender.width(key) * 0.75f + 8;
            right -= kw;
            Render2D.roundedRect(ctx, right, y + 12, kw, 13, 3, 0x14FFFFFF);
            TextRender.drawScaled(ctx, key, right + 4, y + 15.5f, 0.75f, Theme.TEXT_MUTED, false);
            right -= 4;
        }

        float textX = ix + 27;
        TextRender.draw(ctx, TextRender.ellipsize(module.getName(), (int) (right - textX)), textX, y + 10, Theme.TEXT);
        TextRender.drawScaled(ctx, categoryName, textX, y + 21, 0.65f, Theme.TEXT_DIM, false);

        wrap(width - 18);
        TextRender.draw(ctx, line1, x + 9, y + 36, Theme.TEXT_MUTED);
        if (!line2.isEmpty()) TextRender.draw(ctx, line2, x + 9, y + 47, Theme.TEXT_MUTED);

        if (h > 0.01f) {
            Render2D.pushAlpha(h);
            IconRender.draw(ctx, Icon.CHEVRON_RIGHT, x + width - 15, y + height - 14, 8, Theme.TEXT_DIM);
            Render2D.popAlpha();
        }
        Render2D.popAlpha();
        if (isHovered(mx, my) && !toggle.isHovered(mx, my)) {
            UiContext.tooltip("Click: settings · Middle-click: toggle", mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isHovered(mx, my)) return false;
        if (toggle.mouseClicked(mx, my, button)) return true;
        if (button == 2) {
            module.toggle();
            Button.playClick();
            return true;
        }
        if (button == 0 || button == 1) {
            Button.playClick();
            openSettings.accept(module);
            return true;
        }
        return false;
    }

    public boolean contains(double mx, double my) {
        return MathUtil.inside(mx, my, x, y, width, height);
    }
}
