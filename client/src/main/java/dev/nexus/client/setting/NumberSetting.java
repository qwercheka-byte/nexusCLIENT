package dev.nexus.client.setting;

import dev.nexus.client.util.Lang;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nexus.client.util.MathUtil;

import java.util.Locale;

/** A bounded numeric value edited with a slider. */
public final class NumberSetting extends Setting<Double> {
    private final double min, max, step;
    private final int decimals;
    private String suffix = "";
    private double displayMultiplier = 1;

    public NumberSetting(String id, String name, String description, double defaultValue, double min, double max, double step) {
        super(id, name, description, checkRange(defaultValue, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = MathUtil.decimalsFor(step);
        revalidateDefault();
    }

    private static double checkRange(double def, double min, double max) {
        if (min >= max) throw new IllegalArgumentException("min must be < max");
        return def;
    }

    @Override
    protected Double validate(Double candidate) {
        if (candidate == null || candidate.isNaN() || candidate.isInfinite()) return null;
        // during super-construction bounds are still 0 -> accept as is, corrected right after
        if (min == 0 && max == 0) return candidate;
        double v = MathUtil.roundToStep(candidate - min, step) + min;
        v = MathUtil.clamp(v, min, max);
        // remove floating point noise
        double scale = Math.pow(10, decimals + 2);
        return Math.round(v * scale) / scale;
    }

    /** Appends a unit to the displayed value, e.g. "px" or "%". */
    public NumberSetting suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    /** Multiplies the displayed value (e.g. 100 to show 0.5 as 50%). Does not affect the stored value. */
    public NumberSetting displayMultiplier(double multiplier) {
        this.displayMultiplier = multiplier;
        return this;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public float getFloat() { return value.floatValue(); }
    public int getInt() { return (int) Math.round(value); }

    /** Normalised position of the value inside its range (0..1). */
    public double getProgress() {
        return (value - min) / (max - min);
    }

    public void setProgress(double progress) {
        set(min + MathUtil.clamp(progress, 0, 1) * (max - min));
    }

    private double formattedFor = Double.NaN;
    private String formatted = "";
    private int formattedLang = -1;

    /** Display text, cached until the value changes (safe to call every frame). */
    public String format() {
        if (value == formattedFor && formattedLang == Lang.revision()) return formatted;
        formattedFor = value;
        formattedLang = Lang.revision();
        formatted = formatUncached();
        return formatted;
    }

    private String formatUncached() {
        double shown = value * displayMultiplier;
        int shownDecimals = displayMultiplier == 1 ? decimals : Math.max(0, decimals - (int) Math.round(Math.log10(displayMultiplier)));
        return String.format(Locale.ROOT, "%." + shownDecimals + "f", shown) + Lang.tr(suffix);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsDouble());
    }
}
