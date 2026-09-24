package dev.vantage.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A numeric value with a range and a step. Every number in the client is one of these, which is
 * what makes the sliders in the GUI editable rather than hardcoded.
 */
public class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;
    private final String suffix;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        this(name, description, defaultValue, min, max, step, "");
    }

    public NumberSetting(String name, String description, double defaultValue,
                         double min, double max, double step, String suffix) {
        super(name, description, defaultValue);
        if (min >= max) {
            throw new IllegalArgumentException("setting '" + name + "': min must be below max");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("setting '" + name + "': step must be positive");
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix == null ? "" : suffix;
        // The default was stored before the bounds existed, so re-apply them now.
        set(defaultValue);
    }

    @Override
    protected Double coerce(Double candidate) {
        double clamped = Math.max(min, Math.min(max, candidate));
        // Snap to the nearest step so a slider drag cannot produce 0.30000000000000004.
        double snapped = min + Math.round((clamped - min) / step) * step;
        // Round off the accumulated binary error from that multiply.
        double rounded = Math.round(snapped * 1_000_000.0) / 1_000_000.0;
        return Math.max(min, Math.min(max, rounded));
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public String getSuffix() {
        return suffix;
    }

    /** Where the current value sits in its range, as 0..1. Used to position the slider knob. */
    public double getFraction() {
        return (get() - min) / (max - min);
    }

    public void setFraction(double fraction) {
        set(min + (max - min) * Math.max(0.0, Math.min(1.0, fraction)));
    }

    public double asDouble() {
        return get();
    }

    public float asFloat() {
        return get().floatValue();
    }

    public int asInt() {
        return (int) Math.round(get());
    }

    /** True when the step is whole, so the GUI can drop the decimal point. */
    public boolean isIntegral() {
        return step == Math.floor(step) && min == Math.floor(min) && max == Math.floor(max);
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            try {
                set(json.getAsDouble());
            } catch (NumberFormatException ignored) {
                // Leave the current value; a corrupt entry should not break startup.
            }
        }
    }
}
