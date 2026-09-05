package dev.vantage.module.impl.utility;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

/** Keeps sprint on without holding the key. */
public class ToggleSprintModule extends Module {

    private final BooleanSetting whileSneaking = register(new BooleanSetting(
            "While Sneaking", "Keep sprinting even when sneaking", false));

    public ToggleSprintModule() {
        super("Toggle Sprint", Category.UTILITY, "Sprint without holding the key");
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        // Only while actually moving forward, so the player still walks when they mean to.
        boolean movingForward = mc.thePlayer.moveForward > 0.0f;
        boolean blocked = mc.thePlayer.isSneaking() && !whileSneaking.value();
        if (movingForward && !blocked && !mc.thePlayer.isCollidedHorizontally) {
            mc.thePlayer.setSprinting(true);
        }
    }
}
