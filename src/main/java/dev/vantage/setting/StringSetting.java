package dev.vantage.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A free-text value.
 *
 * <p>Marking a setting {@linkplain #isMasked() masked} tells the GUI to render bullets instead of
 * the characters. The Hypixel API key uses that.
 */
public class StringSetting extends Setting<String> {

    private final int maxLength;
    private final boolean masked;

    public StringSetting(String name, String description, String defaultValue) {
        this(name, description, defaultValue, 256, false);
    }

    public StringSetting(String name, String description, String defaultValue, int maxLength, boolean masked) {
        super(name, description, defaultValue);
        this.maxLength = maxLength;
        this.masked = masked;
    }

    @Override
    protected String coerce(String candidate) {
        return candidate.length() > maxLength ? candidate.substring(0, maxLength) : candidate;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public boolean isMasked() {
        return masked;
    }

    public boolean isBlank() {
        return get().trim().isEmpty();
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get());
    }

    @Override
    public void read(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(json.getAsString());
        }
    }
}
