package dev.vantage.module.impl.client;

import dev.vantage.hud.HudEditScreen;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.Minecraft;

/** Opens the HUD editor. Like the ClickGUI, this is an action rather than a lasting state. */
public class HudEditorModule extends Module {

    public HudEditorModule() {
        super("HUD Editor", Category.CLIENT, "Drag on-screen elements into place");
    }

    @Override
    protected void onEnable() {
        Minecraft.getMinecraft().displayGuiScreen(new HudEditScreen());
        setEnabledSilently(false);
    }
}
