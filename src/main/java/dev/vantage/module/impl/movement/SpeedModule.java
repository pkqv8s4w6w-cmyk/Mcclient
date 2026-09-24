package dev.vantage.module.impl.movement;

import dev.vantage.event.MoveEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Moves faster than sprinting.
 *
 * <p>Strafe hops and keeps full speed in the air, turning instantly with the keys. Ground stays on
 * the ground, which reads as ordinary sprinting to anyone watching. Vanilla just sets a speed.
 * A vanilla server only rejects moves over ten blocks in a packet, so all three stay well clear.
 */
public class SpeedModule extends Module {

    public enum Mode { STRAFE, GROUND, VANILLA }

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "How the extra speed is gained", Mode.STRAFE));
    private final NumberSetting multiplier = register(new NumberSetting(
            "Speed", "Multiple of sprint speed", 1.5, 1.0, 4.0, 0.05, "x"));
    private final NumberSetting hopHeight = register(new NumberSetting(
            "Hop Height", "Jump strength for Strafe; 0.42 is a normal jump", 0.42, 0.2, 0.42, 0.01));

    private double airSpeed;

    public SpeedModule() {
        super("Speed", Category.MOVEMENT, "Move faster than sprinting");
        markBlatant();
        hopHeight.visibleWhen(() -> mode.get() == Mode.STRAFE);
        on(MoveEvent.class, this::onMove);
    }

    @Override
    public String getSuffix() {
        return mode.currentLabel();
    }

    private void onMove(MoveEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player.isInWater() || player.isInLava() || player.isOnLadder() || player.isRiding()
                || player.capabilities.isFlying) {
            return;
        }
        double target = MovementUtil.baseSpeed() * multiplier.asDouble();
        switch (mode.get()) {
            case STRAFE:
                if (!MovementUtil.isMoving()) {
                    return;
                }
                if (player.onGround) {
                    player.motionY = hopHeight.asDouble();
                    event.setY(player.motionY);
                    airSpeed = target;
                } else {
                    // Air friction would bleed this away; decay it slowly instead of at vanilla's rate.
                    airSpeed = Math.max(MovementUtil.baseSpeed(), airSpeed * 0.985);
                }
                MovementUtil.setSpeed(event, airSpeed);
                break;
            case GROUND:
                if (player.onGround && MovementUtil.isMoving()) {
                    MovementUtil.setSpeed(event, target);
                }
                break;
            default:
                MovementUtil.setSpeed(event, target);
        }
    }
}
