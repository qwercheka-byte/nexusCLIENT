package dev.nexus.client.ui.component;

import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.theme.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Small uppercase caption with a divider line, used to group rows. */
public class SectionHeader extends Component {
    private final String title;

    public SectionHeader(String title) {
        this.title = title.toUpperCase(Locale.ROOT);
        this.height = 18;
    }

    @Override
    public float preferredHeight(float width) {
        return 18;
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        float ty = y + 7;
        TextRender.drawScaled(ctx, title, x + 2, ty, 0.75f, Theme.accentBright(), false);
        float tw = TextRender.width(title) * 0.75f;
        Render2D.rect(ctx, x + tw + 8, ty + 3, width - tw - 10, 1, Theme.DIVIDER);
    }
}
