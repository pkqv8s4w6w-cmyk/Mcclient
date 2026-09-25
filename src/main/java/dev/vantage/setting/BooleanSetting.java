package dev.vantage.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An on/off toggle. */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean value() {
        return get();
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(json.getAsBoolean());
        }
    }
}
