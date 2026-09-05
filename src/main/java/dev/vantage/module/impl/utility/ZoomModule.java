package dev.vantage.module.impl.utility;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.KeybindSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Hold a key to narrow the field of view. */
public class ZoomModule extends Module {

    private final NumberSetting level = register(new NumberSetting(
            "Level", "How far to zoom in", 4.0, 1.5, 10.0, 0.5, "x"));
    private final BooleanSetting slowMouse = register(new BooleanSetting(
            "Slow Mouse", "Reduce sensitivity while zoomed so aiming stays proportional", true));
    private final KeybindSetting zoomKey = register(new KeybindSetting(
            "Zoom Key", "Held, not toggled", Keyboard.KEY_C));

    private float originalFov;
    private float originalSensitivity;
    private boolean zooming;

    public ZoomModule() {
        super("Zoom", Category.UTILITY, "Hold a key to zoom in");
    }

    @Override
    public void onTick() {
        boolean held = zoomKey.isBound() && Keyboard.isKeyDown(zoomKey.get());
        if (held == zooming) {
            return;
        }
        zooming = held;
        Minecraft mc = Minecraft.getMinecraft();
        if (held) {
            originalFov = mc.gameSettings.fovSetting;
            originalSensitivity = mc.gameSettings.mouseSensitivity;
            mc.gameSettings.fovSetting = (float) (originalFov / level.asDouble());
            if (slowMouse.value()) {
                mc.gameSettings.mouseSensitivity = (float) (originalSensitivity / level.asDouble());
            }
        } else {
            restore(mc);
        }
    }

    @Override
    protected void onDisable() {
        if (zooming) {
            restore(Minecraft.getMinecraft());
            zooming = false;
        }
    }

    /** Always put the player's own settings back; leaving them zoomed would be maddening. */
    private void restore(Minecraft mc) {
        mc.gameSettings.fovSetting = originalFov;
        mc.gameSettings.mouseSensitivity = originalSensitivity;
    }
}
