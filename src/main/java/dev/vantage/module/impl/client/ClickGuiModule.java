package dev.vantage.module.impl.client;

import dev.vantage.gui.ClickGui;
import dev.vantage.gui.Theme;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.ColorSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/**
 * Opens the menu and owns its colours.
 *
 * <p>Modelled as a module so its key is rebindable through the same mechanism as everything else.
 * Enabling it opens the screen and immediately clears the flag, so the key always reads as "open"
 * rather than toggling a state nobody can see.
 *
 * <p>The colours are bound into {@link Theme} rather than copied, so changing one restyles the
 * whole client while the picker is still being dragged.
 */
public class ClickGuiModule extends Module {

    /** A named set of colours the Themes page can apply in one click. */
    public enum Preset {
        PRESTIGE("Prestige", 0xFF8B7CF6, 0xFF0D0D12, 0xFF16161E, 0xFFE9E9F0),
        MIDNIGHT("Midnight", 0xFF4C8DFF, 0xFF0E0F12, 0xFF16181D, 0xFFE8EAED),
        ROSE("Rose", 0xFFF472B6, 0xFF120D10, 0xFF1D151A, 0xFFF3E8EE),
        MINT("Mint", 0xFF34D399, 0xFF0C110F, 0xFF141C18, 0xFFE6F2EC),
        SUNSET("Sunset", 0xFFFB923C, 0xFF120F0C, 0xFF1D1712, 0xFFF4EDE6),
        MONO("Mono", 0xFFE5E5E5, 0xFF0B0B0C, 0xFF161617, 0xFFEDEDED);

        public final String label;
        public final int accent;
        public final int panel;
        public final int row;
        public final int text;

        Preset(String label, int accent, int panel, int row, int text) {
            this.label = label;
            this.accent = accent;
            this.panel = panel;
            this.row = row;
            this.text = text;
        }
    }

    /** The accent every profile had before the Prestige theme became the default. */
    private static final int OLD_DEFAULT_ACCENT = 0xFF4C8DFF;

    private final ColorSetting accent = register(new ColorSetting(
            "Accent", "Active states, sliders and highlights", Preset.PRESTIGE.accent));
    private final ColorSetting panel = register(new ColorSetting(
            "Panel", "Window background", Preset.PRESTIGE.panel));
    private final ColorSetting row = register(new ColorSetting(
            "Row", "Card and field background", Preset.PRESTIGE.row));
    private final ColorSetting text = register(new ColorSetting(
            "Text", "Primary text; muted shades are derived from it", Preset.PRESTIGE.text));
    private final ColorSetting lowThreat = register(new ColorSetting(
            "Low Threat", "Colour for a score of 0, and for good news", 0xFF4ADE80));
    private final ColorSetting highThreat = register(new ColorSetting(
            "High Threat", "Colour for a score of 10, and for warnings", 0xFFFF5A5A));

    private final EnumSetting<ClickGui.Background> background = register(new EnumSetting<ClickGui.Background>(
            "Background", "What to draw behind the window", ClickGui.Background.DIM));
    private final NumberSetting uiScale = register(new NumberSetting(
            "UI Scale", "Size of the menu relative to the screen", 100.0, 70.0, 130.0, 5.0, "%"));

    private ClickGui screen;

    public ClickGuiModule() {
        super("ClickGUI", Category.CLIENT, "Opens this menu");
        getKeybind().set(Keyboard.KEY_RSHIFT);
        Theme.bindAccent(accent);
        Theme.bindPanel(panel);
        Theme.bindRow(row);
        Theme.bindText(text);
        Theme.bindThreatRange(lowThreat, highThreat);
    }

    @Override
    protected void onEnable() {
        if (screen == null) {
            screen = new ClickGui();
        }
        Minecraft.getMinecraft().displayGuiScreen(screen);
        // Clear the flag without firing onDisable: the module is an action, not a state.
        setEnabledSilently(false);
    }

    public ClickGui.Background getBackground() {
        return background.get();
    }

    public float getUiScale() {
        return uiScale.asFloat() / 100.0f;
    }

    public ColorSetting[] colourSettings() {
        return new ColorSetting[]{accent, panel, row, text, lowThreat, highThreat};
    }

    public EnumSetting<ClickGui.Background> backgroundSetting() {
        return background;
    }

    public NumberSetting uiScaleSetting() {
        return uiScale;
    }

    public void applyPreset(Preset preset) {
        accent.setRainbow(false);
        accent.set(preset.accent);
        panel.set(preset.panel);
        row.set(preset.row);
        text.set(preset.text);
    }

    /** Whether the current colours are exactly a preset's, for marking it on the Themes page. */
    public boolean matches(Preset preset) {
        return !accent.isRainbow() && accent.get() == preset.accent && panel.get() == preset.panel
                && row.get() == preset.row && text.get() == preset.text;
    }

    /**
     * Profiles saved before this version store the old blue theme explicitly. Anyone still on it
     * never chose it, so move them to the new default rather than leave them on the old look.
     */
    public void migrateOldDefaults() {
        if (accent.get() == OLD_DEFAULT_ACCENT && panel.get() == Preset.MIDNIGHT.panel
                && row.get() == Preset.MIDNIGHT.row && text.get() == Preset.MIDNIGHT.text) {
            applyPreset(Preset.PRESTIGE);
        }
    }
}
