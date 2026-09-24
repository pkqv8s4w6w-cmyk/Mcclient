package dev.vantage.module.impl.utility;

import dev.vantage.event.SaveOptionsEvent;
import dev.vantage.event.Stage;
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
        // A zoomed field of view must never be what gets written to options.txt.
        on(SaveOptionsEvent.class, event -> {
            if (!zooming) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (event.getStage() == Stage.PRE) {
                mc.gameSettings.fovSetting = originalFov;
                mc.gameSettings.mouseSensitivity = originalSensitivity;
            } else {
                applyZoom(mc);
            }
        });
    }

    @Override
    public void onTick() {
        // Only with no screen open: the key is a letter, and typing it in chat must not zoom.
        Minecraft mc = Minecraft.getMinecraft();
        boolean held = zoomKey.isBound() && mc.currentScreen == null && Keyboard.isKeyDown(zoomKey.get());
        if (held == zooming) {
            return;
        }
        zooming = held;
        if (held) {
            originalFov = mc.gameSettings.fovSetting;
            originalSensitivity = mc.gameSettings.mouseSensitivity;
            applyZoom(mc);
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

    private void applyZoom(Minecraft mc) {
        mc.gameSettings.fovSetting = (float) (originalFov / level.asDouble());
        if (slowMouse.value()) {
            mc.gameSettings.mouseSensitivity = (float) (originalSensitivity / level.asDouble());
        }
    }

    /** Always put the player's own settings back; leaving them zoomed would be maddening. */
    private void restore(Minecraft mc) {
        mc.gameSettings.fovSetting = originalFov;
        mc.gameSettings.mouseSensitivity = originalSensitivity;
    }
}
