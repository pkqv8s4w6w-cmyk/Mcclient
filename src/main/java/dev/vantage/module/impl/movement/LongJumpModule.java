package dev.vantage.module.impl.movement;

import dev.vantage.event.JumpEvent;
import dev.vantage.event.MoveEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Jumps much further. The boost is applied at take-off and carried through the air. */
public class LongJumpModule extends Module {

    private final NumberSetting boost = register(new NumberSetting(
            "Boost", "Take-off speed as a multiple of sprinting", 3.0, 1.0, 8.0, 0.1, "x"));
    private final NumberSetting height = register(new NumberSetting(
            "Height", "Jump strength; 0.42 is normal", 0.42, 0.3, 1.0, 0.01));
    private final BooleanSetting disableOnLand = register(new BooleanSetting(
            "Disable On Land", "Switch off after one jump", true));

    private boolean airborne;
    private double speed;

    public LongJumpModule() {
        super("LongJump", Category.MOVEMENT, "Jump much further");
        markBlatant();
        on(JumpEvent.class, this::onJump);
        on(MoveEvent.class, this::onMove);
    }

    private void onJump(JumpEvent event) {
        if (!MovementUtil.isMoving()) {
            return;
        }
        event.setMotionY(height.asFloat());
        speed = MovementUtil.baseSpeed() * boost.asDouble();
        airborne = true;
    }

    private void onMove(MoveEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (!airborne) {
            return;
        }
        if (player.onGround && player.motionY <= 0.0) {
            airborne = false;
            if (disableOnLand.value()) {
                setEnabled(false);
            }
            return;
        }
        speed = Math.max(MovementUtil.baseSpeed(), speed * 0.98);
        MovementUtil.setSpeed(event, speed);
    }
}
