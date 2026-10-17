package dev.nexus.client.module.impl.visuals;

import dev.nexus.client.Nexus;
import dev.nexus.client.event.events.TickEvent;
import dev.nexus.client.module.Category;
import dev.nexus.client.module.Module;
import dev.nexus.client.setting.BooleanSetting;
import dev.nexus.client.setting.NumberSetting;
import dev.nexus.client.ui.animation.Animation;
import dev.nexus.client.ui.animation.Easing;
import net.minecraft.client.option.SimpleOption;

import java.util.function.Consumer;

/**
 * Raises the gamma option above the vanilla maximum.
 * <p>
 * The user's original gamma is remembered and restored on disable and shutdown, and it is what gets
 * written to options.txt (see GameOptionsMixin). If the user moves the vanilla brightness slider
 * while Fullbright is active, that new value becomes the value to restore.
 */
public final class FullbrightModule extends Module {
    private final NumberSetting brightness = add(new NumberSetting("brightness", "Brightness",
            "Gamma level while active (vanilla maximum is 100%)", 10.0, 1.0, 15.0, 0.5).displayMultiplier(100).suffix("%"));
    private final BooleanSetting smooth = add(new BooleanSetting("smooth", "Smooth transition",
            "Fade the brightness in and out", true));

    private final Animation level = new Animation(0f, 450, Easing.OUT_CUBIC);
    private double original = Double.NaN;
    private double lastApplied = Double.NaN;
    private Double stashedForWrite;
    private final Consumer<TickEvent> fadeOut = e -> tickFadeOut();
    private boolean fadingOut;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Lets you see in the dark by raising gamma. Restores your brightness when disabled.",
                Category.VISUALS);
        on(TickEvent.class, e -> apply());
        brightness.onChange(v -> {
            if (isEnabled()) level.animateTo(v.floatValue());
        });
    }

    private SimpleOption<Double> gamma() {
        return mc.options == null ? null : mc.options.getGamma();
    }

    @Override
    protected void onEnable() {
        SimpleOption<Double> gamma = gamma();
        if (gamma == null) return;
        if (fadingOut) stopFadeOut();
        if (Double.isNaN(original)) {
            original = gamma.getValue();
            level.set((float) original);
        }
        if (smooth.isOn()) level.animateTo(brightness.getFloat());
        else level.set(brightness.getFloat());
        apply();
    }

    @Override
    protected void onDisable() {
        if (Double.isNaN(original)) return;
        if (smooth.isOn() && mc.world != null && !fadingOut) {
            level.animateTo((float) original, 300);
            fadingOut = true;
            Nexus.events().subscribe(TickEvent.class, fadeOut);
        } else {
            restore();
        }
    }

    private void apply() {
        SimpleOption<Double> gamma = gamma();
        if (gamma == null || Double.isNaN(original)) return;
        Object current = gamma.value;
        // the user changed brightness in the vanilla options screen -> remember it as the value to restore
        if (!Double.isNaN(lastApplied) && current instanceof Double d && d != lastApplied && d >= 0 && d <= 1) {
            original = d;
        }
        double desired = level.get();
        gamma.value = desired;
        lastApplied = desired;
    }

    private void tickFadeOut() {
        apply();
        if (level.isFinished()) restore();
    }

    private void stopFadeOut() {
        fadingOut = false;
        Nexus.events().unsubscribe(TickEvent.class, fadeOut);
    }

    /** Puts the original gamma back through the regular option API. */
    private void restore() {
        if (fadingOut) stopFadeOut();
        SimpleOption<Double> gamma = gamma();
        if (gamma != null && !Double.isNaN(original)) {
            double value = Math.max(0, Math.min(1, original));
            gamma.value = value;
            gamma.setValue(value);
        }
        original = Double.NaN;
        lastApplied = Double.NaN;
    }

    // ------------------------------------------------------------------ options.txt guard

    public void beforeOptionsWrite() {
        SimpleOption<Double> gamma = gamma();
        if (gamma == null || Double.isNaN(original)) return;
        stashedForWrite = (Double) gamma.value;
        gamma.value = Math.max(0, Math.min(1, original));
    }

    public void afterOptionsWrite() {
        SimpleOption<Double> gamma = gamma();
        if (gamma == null || stashedForWrite == null) return;
        gamma.value = stashedForWrite;
        stashedForWrite = null;
    }
}
