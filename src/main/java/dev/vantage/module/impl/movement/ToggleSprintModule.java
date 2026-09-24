package dev.vantage.module.impl.movement;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.potion.Potion;

/**
 * Keeps sprint on without holding the key.
 *
 * <p>Follows vanilla's own rules for when sprinting is possible - not while hungry, blind, using an
 * item or walking into a wall - so it never sprints where the key would not. Omni lifts the
 * forward-only rule for sprinting sideways and backwards, which the server does not check.
 */
public class ToggleSprintModule extends Module {

    private final BooleanSetting whileSneaking = register(new BooleanSetting(
            "While Sneaking", "Keep sprinting even when sneaking", false));
    private final BooleanSetting omni = register(new BooleanSetting(
            "Omni", "Sprint in every direction, not just forward", false));

    public ToggleSprintModule() {
        super("Toggle Sprint", Category.MOVEMENT, "Sprint without holding the key");
    }

    @Override
    public void onTick() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        boolean moving = omni.value()
                ? player.movementInput.moveForward != 0.0f || player.movementInput.moveStrafe != 0.0f
                : player.movementInput.moveForward >= 0.8f;
        boolean hungry = player.getFoodStats().getFoodLevel() <= 6 && !player.capabilities.allowFlying;
        boolean blocked = (player.isSneaking() && !whileSneaking.value())
                || hungry
                || player.isPotionActive(Potion.blindness)
                || player.isCollidedHorizontally
                || (player.isUsingItem() && !omni.value());
        if (moving && !blocked) {
            player.setSprinting(true);
        }
    }
}
