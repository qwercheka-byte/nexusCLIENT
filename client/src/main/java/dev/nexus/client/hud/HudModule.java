package dev.nexus.client.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * A module that draws an element on the HUD.
 * <p>
 * Positions are stored relative to an anchor (left/centre/right × top/middle/bottom) chosen from the
 * element's location, so layouts survive window resizes and GUI scale changes. Elements are always
 * clamped inside the screen.
 */
public abstract class HudModule extends Module {
    public static final int START = 0, CENTER = 1, END = 2;

    protected final NumberSetting scale = add(new NumberSetting("scale", "Scale", "Size of the element",
            1.0, 0.5, 2.5, 0.05).displayMultiplier(100).suffix("%"));
    protected final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", "Transparency of the element",
            1.0, 0.1, 1.0, 0.05).displayMultiplier(100).suffix("%"));
    protected final BooleanSetting background = add(new BooleanSetting("background", "Background",
            "Draw a panel behind the element", true));

    private final int defaultAnchorX, defaultAnchorY;
    private final float defaultOffsetX, defaultOffsetY;
    private int anchorX, anchorY;
    private float offsetX, offsetY;
    /** Unscaled size reported by the last render. */
    protected float width = 40, height = 14;

    protected HudModule(String id, String name, String description, boolean enabledByDefault,
                        int anchorX, int anchorY, float offsetX, float offsetY) {
        super(id, name, description, Category.HUD, enabledByDefault);
        this.defaultAnchorX = anchorX;
        this.defaultAnchorY = anchorY;
        this.defaultOffsetX = offsetX;
        this.defaultOffsetY = offsetY;
        resetPosition();
    }

    /**
     * Draws the element at (0, 0) in unscaled local coordinates and updates {@link #width}/{@link #height}.
     *
     * @param editor true inside the HUD editor: render sample content when there is no real data
     * @return false if nothing was drawn (e.g. no target / no effects)
     */
    public abstract boolean render(DrawContext ctx, boolean editor);

    protected final void setSize(float w, float h) {
        this.width = Math.max(4, w);
        this.height = Math.max(4, h);
    }

    // ------------------------------------------------------------------ geometry

    public float getScale() { return scale.getFloat(); }
    public float getOpacity() { return opacity.getFloat(); }
    public boolean hasBackground() { return background.isOn(); }
    public float scaledWidth() { return width * getScale(); }
    public float scaledHeight() { return height * getScale(); }

    public float getX(float screenWidth) {
        float w = scaledWidth();
        float x = switch (anchorX) {
            case CENTER -> screenWidth / 2f - w / 2f + offsetX;
            case END -> screenWidth - w - offsetX;
            default -> offsetX;
        };
        return MathUtil.clamp(x, 0, Math.max(0, screenWidth - w));
    }

    public float getY(float screenHeight) {
        float h = scaledHeight();
        float y = switch (anchorY) {
            case CENTER -> screenHeight / 2f - h / 2f + offsetY;
            case END -> screenHeight - h - offsetY;
            default -> offsetY;
        };
        return MathUtil.clamp(y, 0, Math.max(0, screenHeight - h));
    }

    /** Stores an absolute top-left position; the anchor is derived from the element's centre. */
    public void setPosition(float x, float y, float screenWidth, float screenHeight) {
        float w = scaledWidth(), h = scaledHeight();
        x = MathUtil.clamp(x, 0, Math.max(0, screenWidth - w));
        y = MathUtil.clamp(y, 0, Math.max(0, screenHeight - h));
        float cx = x + w / 2f, cy = y + h / 2f;
        anchorX = cx < screenWidth / 3f ? START : cx > screenWidth * 2f / 3f ? END : CENTER;
        anchorY = cy < screenHeight / 3f ? START : cy > screenHeight * 2f / 3f ? END : CENTER;
        offsetX = switch (anchorX) {
            case CENTER -> x + w / 2f - screenWidth / 2f;
            case END -> screenWidth - x - w;
            default -> x;
        };
        offsetY = switch (anchorY) {
            case CENTER -> y + h / 2f - screenHeight / 2f;
            case END -> screenHeight - y - h;
            default -> y;
        };
        onSettingChanged(scale);
    }

    /** Changes the scale keeping the element's centre in place. */
    public void setScaleKeepingCenter(double newScale, float screenWidth, float screenHeight) {
        float cx = getX(screenWidth) + scaledWidth() / 2f, cy = getY(screenHeight) + scaledHeight() / 2f;
        scale.set(newScale);
        setPosition(cx - scaledWidth() / 2f, cy - scaledHeight() / 2f, screenWidth, screenHeight);
    }

    public NumberSetting scaleSetting() {
        return scale;
    }

    public void resetPosition() {
        anchorX = defaultAnchorX;
        anchorY = defaultAnchorY;
        offsetX = defaultOffsetX;
        offsetY = defaultOffsetY;
    }

    /** Resets position and scale and persists. */
    public void resetLayout() {
        resetPosition();
        scale.reset();
        onSettingChanged(scale);
    }

    /** True if the element sits on the right third – content can align to the right edge. */
    public boolean isRightAligned() {
        return anchorX == END;
    }

    public boolean isBottomAligned() {
        return anchorY == END;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void resetSettings() {
        super.resetSettings();
        resetPosition();
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = super.toJson();
        JsonObject hud = new JsonObject();
        hud.addProperty("ax", anchorX);
        hud.addProperty("ay", anchorY);
        hud.addProperty("ox", offsetX);
        hud.addProperty("oy", offsetY);
        obj.add("hud", hud);
        return obj;
    }

    @Override
    public void fromJson(JsonObject obj) {
        super.fromJson(obj);
        if (obj == null) return;
        JsonElement e = obj.get("hud");
        if (e == null || !e.isJsonObject()) return;
        JsonObject hud = e.getAsJsonObject();
        try {
            int ax = hud.has("ax") ? hud.get("ax").getAsInt() : defaultAnchorX;
            int ay = hud.has("ay") ? hud.get("ay").getAsInt() : defaultAnchorY;
            float ox = hud.has("ox") ? hud.get("ox").getAsFloat() : defaultOffsetX;
            float oy = hud.has("oy") ? hud.get("oy").getAsFloat() : defaultOffsetY;
            if (ax < START || ax > END || ay < START || ay > END || !Float.isFinite(ox) || !Float.isFinite(oy)) return;
            // reject absurd offsets (corrupted values) – the element would be clamped anyway
            if (Math.abs(ox) > 10000 || Math.abs(oy) > 10000) return;
            anchorX = ax;
            anchorY = ay;
            offsetX = ox;
            offsetY = oy;
            if (hud.has("scale")) scale.fromJson(hud.get("scale"));
        } catch (RuntimeException ignored) {
            resetPosition();
        }
    }
}
