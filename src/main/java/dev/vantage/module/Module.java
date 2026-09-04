package dev.vantage.module;

import dev.vantage.setting.KeybindSetting;
import dev.vantage.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A single toggleable feature.
 *
 * <p>Modules hold no Minecraft references of their own. {@code ModuleManager} owns the Forge event
 * subscriptions and calls the hooks below, which keeps this class testable off-thread and out of
 * the game.
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;

    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeybindSetting keybind = new KeybindSetting("Keybind", "Key that toggles this module", KeybindSetting.UNBOUND);

    private boolean enabled;

    protected Module(String name, Category category, String description) {
        this.name = name;
        this.category = category;
        this.description = description == null ? "" : description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    /** Stable identifier used as the config key, unaffected by renaming the display label. */
    public String getConfigKey() {
        return name.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public KeybindSetting getKeybind() {
        return keybind;
    }

    // -- settings ---------------------------------------------------------------------------

    /** Registers a setting and hands it straight back, so fields can be assigned inline. */
    protected <S extends Setting<?>> S register(S setting) {
        settings.add(setting);
        return setting;
    }

    protected void registerAll(Setting<?>... toRegister) {
        Collections.addAll(settings, toRegister);
    }

    /**
     * Registers a setting that is saved and loaded but never shown in the menu.
     *
     * <p>For state the user edits by some other means - a HUD element's position, which is set by
     * dragging it - so it persists without putting a meaningless slider in the settings list.
     */
    protected <S extends Setting<?>> S registerHidden(S setting) {
        setting.visibleWhen(() -> false);
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    /** Only the settings that currently apply, so the GUI can skip irrelevant sub-options. */
    public List<Setting<?>> getVisibleSettings() {
        List<Setting<?>> visible = new ArrayList<>(settings.size());
        for (Setting<?> setting : settings) {
            if (setting.isVisible()) {
                visible.add(setting);
            }
        }
        return visible;
    }

    public boolean hasSettings() {
        return !settings.isEmpty();
    }

    // -- lifecycle --------------------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Sets the flag without firing the lifecycle hooks. Used when loading a config, where the
     * world does not exist yet and {@link #onEnable()} would touch a null player.
     */
    public final void setEnabledSilently(boolean value) {
        this.enabled = value;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    // -- hooks ------------------------------------------------------------------------------

    /** Once per client tick while enabled. */
    public void onTick() {
    }

    /** Every frame while enabled, in 2D screen space. */
    public void onRenderOverlay(float partialTicks) {
    }

    /** Every frame while enabled, in 3D world space. */
    public void onRenderWorld(float partialTicks) {
    }

    @Override
    public String toString() {
        return "Module(" + name + ")";
    }
}
