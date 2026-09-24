package dev.vantage.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import dev.vantage.util.PacketUtil;
import dev.vantage.util.Simulation;

/** Attacking, and checking whether a rotation actually points at something. */
public final class CombatUtil {

    private CombatUtil() {
    }

    /**
     * Hits an entity the way a click does: swing, then attack. Without the visible swing the
     * animation packet is still sent, since the server expects one with every attack.
     */
    public static void attack(Entity target, boolean visibleSwing) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (visibleSwing) {
            player.swingItem();
        } else {
            PacketUtil.send(new C0APacketAnimation());
        }
        mc.playerController.attackEntity(player, target);
    }

    /**
     * Whether a ray from the eyes along this rotation meets the target's hitbox within range.
     * Used to hold an attack until the server rotation has actually arrived on the target.
     */
    public static boolean rayHits(float yaw, float pitch, Entity target, double range) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        Vec3 eyes = player.getPositionEyes(1.0f);
        double[] direction = Simulation.direction(yaw, pitch);
        Vec3 end = eyes.addVector(direction[0] * range, direction[1] * range, direction[2] * range);
        float border = target.getCollisionBorderSize();
        AxisAlignedBB box = target.getEntityBoundingBox().expand(border, border, border);
        if (box.isVecInside(eyes)) {
            return true;
        }
        MovingObjectPosition hit = box.calculateIntercept(eyes, end);
        return hit != null;
    }
}
