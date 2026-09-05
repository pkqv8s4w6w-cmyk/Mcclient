package dev.vantage.module.impl.client;

import dev.vantage.gui.ClickGui;
import dev.vantage.gui.Theme;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.ColorSetting;
import dev.vantage.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/**
 * Opens the interface and owns its colours.
 *
 * <p>Modelled as a module so its key is rebindable through the same mechanism as everything else.
 * Enabling it opens the screen and immediately clears the flag, so the key always reads as "open"
 * rather than toggling a state nobody can see.
 *
 * <p>This is the one module that carries a lot of settings on purpose. They are all colours, and
 * the theme reads them live, so changing one restyles the whole client while the picker is still
 * being dragged.
 */
public class ClickGuiModule extends Module {

    private final ColorSetting accent = register(new ColorSetting(
            "Accent", "Active states, sliders and highlights", 0xFF4C8DFF));
    private final ColorSetting panel = register(new ColorSetting(
            "Panel", "Window background", 0xFF0E0F12));
    private final ColorSetting row = register(new ColorSetting(
            "Row", "Module row background", 0xFF16181D));
    private final ColorSetting text = register(new ColorSetting(
            "Text", "Primary text; muted shades are derived from it", 0xFFE8EAED));
    private final ColorSetting lowThreat = register(new ColorSetting(
            "Low Threat", "Colour for a score of 0", 0xFF4ADE80));
    private final ColorSetting highThreat = register(new ColorSetting(
            "High Threat", "Colour for a score of 10", 0xFFFF5A5A));

    private final EnumSetting<ClickGui.Background> background = register(new EnumSetting<ClickGui.Background>(
            "Background", "What to draw behind the window", ClickGui.Background.GRADIENT));

    private ClickGui screen;

    public ClickGuiModule() {
        super("ClickGUI", Category.CLIENT, "The menu you are looking at");
        getKeybind().set(Keyboard.KEY_RSHIFT);

        // Bound, not copied: the theme reads these every frame, so a picker drag or an animated
        // colour shows up immediately everywhere rather than on the next reopen.
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
        screen.setBackground(background.get());
        Minecraft.getMinecraft().displayGuiScreen(screen);

        // Clear the flag without firing onDisable: the module is an action, not a state.
        setEnabledSilently(false);
    }
}
