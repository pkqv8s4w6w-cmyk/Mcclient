package dev.vantage.setting;

import com.google.gson.JsonElement;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A single user-editable value belonging to a {@link dev.vantage.module.Module}.
 *
 * <p>Settings deliberately know nothing about Minecraft or about rendering. They hold a value,
 * validate it, serialise it, and report whether they are currently relevant. The GUI decides how
 * to draw them.
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {

    private final String name;
    private final String description;
    private final T defaultValue;

    private T value;
    private BooleanSupplier visibility = () -> true;
    private Consumer<T> listener;

    protected Setting(String name, String description, T defaultValue) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("setting name must not be empty");
        }
        if (defaultValue == null) {
            throw new IllegalArgumentException("setting '" + name + "' must have a default");
        }
        this.name = name;
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** Stable key used in the config file. Renaming a setting's label will not orphan its value. */
    public String getConfigKey() {
        return name.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public T get() {
        return value;
    }

    public T getDefault() {
        return defaultValue;
    }

    public void set(T newValue) {
        if (newValue == null) {
            return;
        }
        T coerced = coerce(newValue);
        if (coerced.equals(value)) {
            return;
        }
        value = coerced;
        if (listener != null) {
            listener.accept(coerced);
        }
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    /**
     * Hook for subclasses that constrain their input, such as clamping a number to its range.
     * Called on every {@link #set}.
     */
    protected T coerce(T candidate) {
        return candidate;
    }

    /**
     * Hides this setting in the GUI while the supplied condition is false. Used for sub-settings
     * that only make sense when their parent toggle is on, so panels stay short instead of
     * showing a wall of irrelevant options.
     */
    public Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return this;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    public Setting<T> onChange(Consumer<T> callback) {
        this.listener = callback;
        return this;
    }

    public abstract JsonElement write();

    /**
     * Restores this setting from JSON. Implementations must tolerate malformed or wrongly typed
     * input by leaving the current value alone; a hand-edited config should not stop the client
     * from starting.
     */
    public abstract void read(JsonElement json);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + name + "=" + value + ")";
    }
}
