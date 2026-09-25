package dev.vantage.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A choice between the constants of an enum, rendered as a dropdown. */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {

    private final Class<E> type;

    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue);
        this.type = defaultValue.getDeclaringClass();
    }

    public E[] getOptions() {
        return type.getEnumConstants();
    }

    public void cycle() {
        E[] options = getOptions();
        set(options[(get().ordinal() + 1) % options.length]);
    }

    /** Enum constants are SCREAMING_CASE; turn them into something readable in the GUI. */
    public static String label(Enum<?> constant) {
        String[] words = constant.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    public String currentLabel() {
        return label(get());
    }

    @Override
    public JsonElement write() {
        return new JsonPrimitive(get().name());
    }

    @Override
    public void read(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) {
            return;
        }
        String name = json.getAsString();
        for (E option : getOptions()) {
            if (option.name().equals(name)) {
                set(option);
                return;
            }
        }
        // Unknown constant: an option that was removed or renamed between versions. Keep the
        // default rather than throwing, so old configs still load.
    }
}
