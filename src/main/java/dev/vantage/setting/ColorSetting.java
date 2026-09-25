package dev.vantage.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * An ARGB colour, optionally animated through the hue wheel.
 *
 * <p>The stored value is the fixed colour. {@link #display()} is what should actually be drawn,
 * since it accounts for rainbow mode.
 */
public class ColorSetting extends Setting<Integer> {

    private boolean rainbow;
    private double rainbowSpeed = 1.0;
    private double hueOffset;

    public ColorSetting(String name, String description, int defaultArgb) {
        super(name, description, defaultArgb);
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public double getRainbowSpeed() {
        return rainbowSpeed;
    }

    public void setRainbowSpeed(double speed) {
        this.rainbowSpeed = Math.max(0.1, Math.min(10.0, speed));
    }

    /**
     * Staggers this element's place in the hue cycle, so a row of rainbow elements sweeps as a
     * gradient instead of all flashing the same colour at once.
     */
    public void setHueOffset(double offset) {
        this.hueOffset = offset;
    }

    /** The colour to draw right now, in ARGB. */
    public int display() {
        if (!rainbow) {
            return get();
        }
        double cycleMillis = 4000.0 / rainbowSpeed;
        double hue = ((System.currentTimeMillis() % (long) cycleMillis) / cycleMillis + hueOffset) % 1.0;
        int rgb = java.awt.Color.HSBtoRGB((float) hue, 0.75f, 1.0f) & 0x00FFFFFF;
        return (getAlpha() << 24) | rgb;
    }

    public int getAlpha() {
        return (get() >> 24) & 0xFF;
    }

    public int getRed() {
        return (get() >> 16) & 0xFF;
    }

    public int getGreen() {
        return (get() >> 8) & 0xFF;
    }

    public int getBlue() {
        return get() & 0xFF;
    }

    public void setAlpha(int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        set((get() & 0x00FFFFFF) | (a << 24));
    }

    public void setRgb(int red, int green, int blue) {
        set((getAlpha() << 24)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF));
    }

    @Override
    public JsonElement write() {
        JsonObject object = new JsonObject();
        object.addProperty("argb", get());
        object.addProperty("rainbow", rainbow);
        object.addProperty("rainbow_speed", rainbowSpeed);
        return object;
    }

    @Override
    public void read(JsonElement json) {
        if (json == null) {
            return;
        }
        // Older configs stored a bare integer; accept both shapes.
        if (json.isJsonPrimitive()) {
            set(json.getAsInt());
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("argb")) {
            set(object.get("argb").getAsInt());
        }
        if (object.has("rainbow")) {
            rainbow = object.get("rainbow").getAsBoolean();
        }
        if (object.has("rainbow_speed")) {
            setRainbowSpeed(object.get("rainbow_speed").getAsDouble());
        }
    }
}
