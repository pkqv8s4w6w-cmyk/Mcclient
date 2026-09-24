package dev.vantage.module.impl.movement;

import dev.vantage.event.UpdateEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;

/**
 * Stops a fall into the void. Once you have fallen a set distance with nothing at all beneath you,
 * Bounce throws you back up and Freeze holds you in the air until you move back over ground.
 *
 * <p>For a fall that is saved by placing a block or throwing a pearl, see Void Clutch.
 */
public class AntiVoidModule extends Module {

    public enum Mode { BOUNCE, FREEZE }

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "Throw yourself back up, or hang in place", Mode.BOUNCE));
    private final NumberSetting distance = register(new NumberSetting(
            "Fall Distance", "How far to fall before stepping in", 4.0, 1.0, 20.0, 0.5, "m"));

    public AntiVoidModule() {
        super("AntiVoid", Category.MOVEMENT, "Catches you before you fall into the void");
        markBlatant();
        on(UpdateEvent.class, event -> onUpdate());
    }

    private void onUpdate() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player.onGround || player.capabilities.isFlying || player.fallDistance < distance.asFloat()
                || !overVoid(player)) {
            return;
        }
        if (mode.get() == Mode.BOUNCE) {
            player.motionY = 1.0;
            player.fallDistance = 0.0f;
        } else {
            player.motionY = 0.0;
        }
    }

    /** True when nothing solid exists anywhere under the player down to the bottom of the world. */
    static boolean overVoid(EntityPlayerSP player) {
        Minecraft mc = Minecraft.getMinecraft();
        for (int y = (int) Math.floor(player.posY); y >= 0; y--) {
            BlockPos pos = new BlockPos(player.posX, y, player.posZ);
            if (!mc.theWorld.isAirBlock(pos)) {
                return false;
            }
        }
        return true;
    }
}
