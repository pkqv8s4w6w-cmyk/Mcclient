package dev.vantage.module.impl.client;

import dev.vantage.gui.ClickGui;
import dev.vantage.gui.Theme;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.ColorSetting;
import dev.vantage.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/**
 * Opens the interface and owns its appearance.
 *
 * <p>Modelled as a module so the ClickGUI's own key is rebindable through the same mechanism as
 * everything else. Enabling it opens the screen and immediately clears the flag, so the key always
 * reads as "open" rather than toggling a state nobody can see.
 */
public class ClickGuiModule extends Module {

    private final ColorSetting accent = register(new ColorSetting(
            "Accent", "Colour used for active states across the client", 0xFF4C8DFF));

    private final EnumSetting<ClickGui.Background> background = register(new EnumSetting<ClickGui.Background>(
            "Background", "What to draw behind the window", ClickGui.Background.GRADIENT));

    private final BooleanSetting collapseOnOpen = register(new BooleanSetting(
            "Collapse On Open", "Close every expanded module each time the menu opens", false));

    private ClickGui screen;

    public ClickGuiModule() {
        super("ClickGUI", Category.CLIENT, "The menu you are looking at");
        getKeybind().set(Keyboard.KEY_RSHIFT);

        // The theme reads this setting directly, so picker drags and rainbow mode both show up
        // immediately without anything having to copy the value each frame.
        Theme.bindAccent(accent);
    }

    @Override
    protected void onEnable() {
        if (screen == null) {
            screen = new ClickGui();
        }
        if (collapseOnOpen.value()) {
            screen.collapseAll();
        }
        screen.setBackground(background.get());
        Minecraft.getMinecraft().displayGuiScreen(screen);

        // Clear the flag without firing onDisable: the module is an action, not a state.
        setEnabledSilently(false);
    }
}
