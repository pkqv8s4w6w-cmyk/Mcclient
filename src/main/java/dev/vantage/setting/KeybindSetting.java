package dev.vantage.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * An LWJGL key code. Zero means unbound.
 *
 * <p>Deliberately stores the raw code rather than a name, so it survives keyboard layout changes.
 * Turning it into something readable is the GUI's job.
 */
public class KeybindSetting extends Setting<Integer> {

    public static final int UNBOUND = 0;

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public boolean isBound() {
        return get() != UNBOUND;
    }

    public void clear() {
        set(UNBOUND);
    }

    public boolean matches(int keyCode) {
        return isBound() && get() == keyCode;
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            try {
                set(json.getAsInt());
            } catch (NumberFormatException ignored) {
                // Keep the default binding.
            }
        }
    }
}
