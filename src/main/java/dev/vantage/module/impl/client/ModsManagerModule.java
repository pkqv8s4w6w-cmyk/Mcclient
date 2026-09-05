package dev.vantage.module.impl.client;

import dev.vantage.mods.ModsScreen;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.Minecraft;

/** Opens the mod list and config editor. */
public class ModsManagerModule extends Module {

    public ModsManagerModule() {
        super("Mods Manager", Category.MODS, "List installed mods and edit their settings");
    }

    @Override
    protected void onEnable() {
        Minecraft.getMinecraft().displayGuiScreen(new ModsScreen());
        setEnabledSilently(false);
    }
}
