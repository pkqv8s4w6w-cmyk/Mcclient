package dev.vantage.module.impl.movement;

import dev.vantage.Vantage;
import dev.vantage.event.MoveEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.module.impl.combat.KillAuraModule;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;

/**
 * Circles KillAura's target at a set distance while you hold a movement key, so you stay in range
 * and hard to hit. Walking into a wall reverses the direction.
 */
public class TargetStrafeModule extends Module {

    private final NumberSetting radius = register(new NumberSetting(
            "Radius", "Distance to circle at", 2.5, 1.0, 5.0, 0.1, "m"));
    private final BooleanSetting onlyJumping = register(new BooleanSetting(
            "Only Jumping", "Only while the jump key is held", false));

    private int direction = 1;

    public TargetStrafeModule() {
        super("TargetStrafe", Category.MOVEMENT, "Circle around your KillAura target");
        markBlatant();
        on(MoveEvent.class, this::onMove);
    }

    private void onMove(MoveEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        KillAuraModule aura = Vantage.instance().modules().get(KillAuraModule.class);
        EntityLivingBase target = aura == null ? null : aura.getTarget();
        if (target == null || !MovementUtil.isMoving() || (onlyJumping.value() && !player.movementInput.jump)) {
            return;
        }
        if (player.isCollidedHorizontally) {
            direction = -direction;
        }
        double speed = Math.max(MovementUtil.horizontalSpeed(), MovementUtil.baseSpeed());
        double dx = player.posX - target.posX;
        double dz = player.posZ - target.posZ;
        double distance = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        // Tangent to the circle, plus a pull toward the ring so the orbit holds its radius.
        double tangentX = -dz / distance * direction;
        double tangentZ = dx / distance * direction;
        double correction = (radius.asDouble() - distance) * 0.5;
        double moveX = tangentX + dx / distance * correction;
        double moveZ = tangentZ + dz / distance * correction;
        double length = Math.max(0.01, Math.sqrt(moveX * moveX + moveZ * moveZ));
        event.setX(moveX / length * speed);
        event.setZ(moveZ / length * speed);
        player.motionX = event.getX();
        player.motionZ = event.getZ();
    }
}
