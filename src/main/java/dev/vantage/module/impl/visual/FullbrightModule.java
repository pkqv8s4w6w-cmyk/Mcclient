package dev.vantage.module.impl.visual;

import dev.vantage.event.SaveOptionsEvent;
import dev.vantage.event.Stage;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * Raises the gamma setting so caves and night are not a guessing game.
 *
 * <p>Gamma is a real game option, so two things have to hold. The player's own value is captured
 * the first time the module runs rather than in the enable hook, because a module switched on by a
 * saved profile never gets that hook. And whenever the game writes options.txt the player's value
 * is swapped back in for the write, so a full-bright gamma never ends up saved - which is how it
 * used to stay stuck on after the module was turned off or the game restarted.
 */
public class FullbrightModule extends Module {

    private final NumberSetting brightness = register(new NumberSetting(
            "Brightness", "How far to raise gamma", 100.0, 1.0, 100.0, 1.0));

    private float originalGamma;
    private boolean captured;

    public FullbrightModule() {
        super("Fullbright", Category.VISUAL, "Brightens the world");
        on(SaveOptionsEvent.class, event -> {
            if (!captured) {
                return;
            }
            Minecraft.getMinecraft().gameSettings.gammaSetting =
                    event.getStage() == Stage.PRE ? originalGamma : brightness.asFloat();
        });
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!captured) {
            originalGamma = mc.gameSettings.gammaSetting;
            captured = true;
        } else if (mc.gameSettings.gammaSetting != brightness.asFloat()) {
            // Changed in the options screen while we were on: that is the player's new choice.
            originalGamma = mc.gameSettings.gammaSetting;
        }
        mc.gameSettings.gammaSetting = brightness.asFloat();
    }

    @Override
    protected void onDisable() {
        if (captured) {
            Minecraft.getMinecraft().gameSettings.gammaSetting = originalGamma;
            captured = false;
        }
    }
}
