package dev.vantage.module;

import dev.vantage.event.EventBus;
import dev.vantage.setting.KeybindSetting;
import dev.vantage.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

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
    private final List<EventBus.Listener<?>> listeners = new ArrayList<>();

    private boolean enabled;
    private boolean blatant;

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

    /**
     * Marks this module as an obvious advantage rather than a quality-of-life tweak. Shown as a tag
     * in the menu so a config can be checked at a glance.
     */
    protected final void markBlatant() {
        blatant = true;
    }

    public boolean isBlatant() {
        return blatant;
    }

    /**
     * A short label shown after the name in the module list, such as the current mode.
     *
     * @return the label, or null for none
     */
    public String getSuffix() {
        return null;
    }

    // -- events -----------------------------------------------------------------------------

    /**
     * Declares a handler for an event on the client's {@link EventBus}. Call from the constructor.
     * The handler is live exactly while the module is enabled.
     */
    protected final <E> void on(Class<E> type, Consumer<E> handler) {
        on(type, EventBus.NORMAL, handler);
    }

    protected final <E> void on(Class<E> type, int priority, Consumer<E> handler) {
        EventBus.Listener<E> listener = new EventBus.Listener<E>(type, priority, handler, this);
        listeners.add(listener);
        if (enabled) {
            EventBus.global().register(listener);
        }
    }

    private void syncListeners() {
        for (EventBus.Listener<?> listener : listeners) {
            if (enabled) {
                EventBus.global().register(listener);
            } else {
                EventBus.global().unregister(listener);
            }
        }
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
        syncListeners();
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
        // Handlers follow the flag even here. A module loaded as enabled from a profile has to
        // actually run; only the one-off enable and disable hooks are skipped.
        syncListeners();
    }

    /**
     * Switches off after a failure without running the disable hook. Handlers stop at once.
     *
     * @return whether the module was on, and so whether {@link #runDisableHook} is owed
     */
    final boolean stopAfterFailure() {
        boolean wasEnabled = enabled;
        enabled = false;
        syncListeners();
        return wasEnabled;
    }

    /**
     * Runs the disable hook for a module stopped by {@link #stopAfterFailure}, so one that changed
     * game state - the timer, flight, the FOV - still puts it back. A failure here is swallowed; the
     * original one has already been reported.
     */
    final void runDisableHook() {
        try {
            onDisable();
        } catch (Throwable ignored) {
            // Nothing more can be done for it.
        }
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

    /** A chat line arrived, colour codes and all. Always the original, never a rewritten one. */
    public void onChatMessage(String raw) {
    }

    /**
     * Rewrites a chat line before it is shown.
     *
     * @return replacement text, or null to leave the line alone
     */
    public String rewriteChat(String raw) {
        return null;
    }

    /** The player moved to a different world, so per-game state should be dropped. */
    public void onWorldChanged() {
    }

    @Override
    public String toString() {
        return "Module(" + name + ")";
    }
}
