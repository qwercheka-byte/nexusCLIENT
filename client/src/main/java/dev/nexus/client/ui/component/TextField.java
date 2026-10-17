package dev.nexus.client.ui.component;

import dev.nexus.client.ui.animation.AnimationClock;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.Scissor;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.setting.StringSetting;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.animation.SmoothValue;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Single line text input with cursor, select-all, clipboard and validation feedback. */
public class TextField extends Component {
    private String text = "";
    private int cursor;
    private boolean allSelected;
    private String placeholder = "";
    private int maxLength = 64;
    private Icon icon;
    private Predicate<String> validator = s -> true;
    private Consumer<String> onChange = s -> {};
    private Consumer<String> onSubmit = s -> {};
    private final SmoothValue focusAnim = new SmoothValue(0f, 18f);
    private float scrollX;
    private StringSetting bound;

    public TextField() {
        this.height = 18;
    }

    /** Creates a field that edits a {@link StringSetting} (invalid input is shown but not applied). */
    public static TextField forSetting(StringSetting setting) {
        TextField f = new TextField();
        f.bound = setting;
        f.text = setting.get();
        f.cursor = f.text.length();
        f.maxLength = setting.getMaxLength();
        f.placeholder = setting.getPlaceholder();
        f.validator = setting::isAcceptable;
        f.onChange = v -> {
            if (setting.isAcceptable(v)) setting.set(v);
        };
        f.height = 16;
        return f;
    }

    public TextField placeholder(String p) { this.placeholder = p; return this; }
    public TextField maxLength(int m) { this.maxLength = m; return this; }
    public TextField icon(Icon icon) { this.icon = icon; return this; }
    public TextField validator(Predicate<String> v) { this.validator = v; return this; }
    public TextField onChange(Consumer<String> c) { this.onChange = c; return this; }
    public TextField onSubmit(Consumer<String> c) { this.onSubmit = c; return this; }

    public String getText() { return text; }

    public void setText(String t) {
        text = t.length() > maxLength ? t.substring(0, maxLength) : t;
        cursor = text.length();
        allSelected = false;
    }

    public boolean isFocused() { return UiContext.isFocused(this); }

    public void focus() {
        UiContext.focus(this);
        cursor = text.length();
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        if (bound != null && !isFocused() && !text.equals(bound.get())) setText(bound.get());
        float h = updateHover(mx, my);
        boolean focused = isFocused();
        float f = focusAnim.update(focused ? 1f : 0f);
        boolean valid = validator.test(text);
        Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS_SMALL, ColorUtil.lerp(0xFF1E1F27, 0xFF23242D, Math.max(h * 0.6f, f)));
        int border = !valid ? Theme.DANGER : ColorUtil.lerp(Theme.BORDER, Theme.accent(), f);
        Render2D.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_SMALL, 1f, border);

        float tx = x + 6;
        if (icon != null) {
            IconRender.draw(ctx, icon, x + 5, y + (height - 10) / 2f, 10, focused ? Theme.accentBright() : Theme.TEXT_DIM);
            tx += 13;
        }
        float innerW = x + width - 5 - tx;
        float ty = TextRender.centerY(y, height);
        Scissor.push(ctx, tx - 1, y, innerW + 2, height);
        if (text.isEmpty()) {
            TextRender.draw(ctx, TextRender.ellipsize(placeholder, (int) innerW), tx, ty, Theme.TEXT_DIM);
        }
        // keep the cursor visible
        float cursorPx = TextRender.width(text.substring(0, Math.min(cursor, text.length())));
        if (cursorPx - scrollX > innerW - 2) scrollX = cursorPx - innerW + 2;
        if (cursorPx - scrollX < 0) scrollX = cursorPx;
        if (TextRender.width(text) < innerW) scrollX = 0;
        float drawX = tx - scrollX;
        if (allSelected && !text.isEmpty() && focused) {
            Render2D.rect(ctx, drawX, y + 3, TextRender.width(text), height - 6, Theme.accentSoft());
        }
        TextRender.draw(ctx, text, drawX, ty, valid ? Theme.TEXT : 0xFFFCA5A5);
        if (focused && AnimationClock.blink(1060)) {
            Render2D.rect(ctx, drawX + cursorPx, y + 4, 1, height - 8, Theme.accentBright());
        }
        Scissor.pop(ctx);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isHovered(mx, my)) {
            if (isFocused()) UiContext.unfocus(this);
            return false;
        }
        if (button == 1) {
            setText("");
            changed();
        }
        UiContext.focus(this);
        allSelected = false;
        float tx = x + 6 + (icon != null ? 13 : 0) - scrollX;
        String trimmed = TextRender.font().trimToWidth(text, (int) Math.max(0, mx - tx + 2));
        cursor = trimmed.length();
        return true;
    }

    private void changed() {
        onChange.accept(text);
    }

    private void insert(String s) {
        StringBuilder clean = new StringBuilder();
        for (char c : s.toCharArray()) if (c >= 32 && c != 127 && c != '\u00A7') clean.append(c);
        if (allSelected) {
            text = "";
            cursor = 0;
            allSelected = false;
        }
        String next = text.substring(0, cursor) + clean + text.substring(cursor);
        if (next.length() > maxLength) return;
        text = next;
        cursor += clean.length();
        changed();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused()) return false;
        insert(String.valueOf(chr));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (!isFocused()) return false;
        boolean ctrl = dev.nexus.client.util.Mc.ctrl(modifiers);
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> UiContext.unfocus(this);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onSubmit.accept(text);
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (allSelected) {
                    setText("");
                    changed();
                } else if (cursor > 0) {
                    int from = ctrl ? wordStart() : cursor - 1;
                    text = text.substring(0, from) + text.substring(cursor);
                    cursor = from;
                    changed();
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (allSelected) {
                    setText("");
                    changed();
                } else if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                    changed();
                }
            }
            case GLFW.GLFW_KEY_LEFT -> { cursor = Math.max(0, ctrl ? wordStart() : cursor - 1); allSelected = false; }
            case GLFW.GLFW_KEY_RIGHT -> { cursor = Math.min(text.length(), cursor + 1); allSelected = false; }
            case GLFW.GLFW_KEY_HOME -> { cursor = 0; allSelected = false; }
            case GLFW.GLFW_KEY_END -> { cursor = text.length(); allSelected = false; }
            case GLFW.GLFW_KEY_A -> { if (ctrl) { allSelected = true; cursor = text.length(); } }
            case GLFW.GLFW_KEY_C -> { if (ctrl) mc.keyboard.setClipboard(text); }
            case GLFW.GLFW_KEY_X -> { if (ctrl) { mc.keyboard.setClipboard(text); setText(""); changed(); } }
            case GLFW.GLFW_KEY_V -> { if (ctrl) insert(mc.keyboard.getClipboard()); }
            default -> { return true; } // swallow other keys while typing (e.g. menu hotkey)
        }
        return true;
    }

    private int wordStart() {
        int i = cursor - 1;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return Math.max(0, i);
    }
}
