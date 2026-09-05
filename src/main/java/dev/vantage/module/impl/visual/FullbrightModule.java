package dev.vantage.module.impl.visual;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Raises the gamma setting so caves and night are not a guessing game. */
public class FullbrightModule extends Module {

    private final NumberSetting brightness = register(new NumberSetting(
            "Brightness", "How far to raise gamma", 100.0, 1.0, 100.0, 1.0));

    private float originalGamma;
    private boolean captured;

    public FullbrightModule() {
        super("Fullbright", Category.VISUAL, "Brightens the world");
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!captured) {
            // Capture once: enabling twice must not save the already-raised value as the original.
            originalGamma = mc.gameSettings.gammaSetting;
            captured = true;
        }
    }

    @Override
    public void onTick() {
        Minecraft.getMinecraft().gameSettings.gammaSetting = brightness.asFloat();
    }

    @Override
    protected void onDisable() {
        if (captured) {
            Minecraft.getMinecraft().gameSettings.gammaSetting = originalGamma;
        }
    }
}
