package dev.vantage.util;

import dev.vantage.event.MoveEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.potion.Potion;

/** Speed and direction for movement modules. Directions follow the camera, not the server yaw. */
public final class MovementUtil {

    /** Horizontal speed of a sprinting player on flat ground, in blocks per tick. */
    public static final double SPRINT_SPEED = 0.2873;

    private MovementUtil() {
    }

    private static EntityPlayerSP player() {
        return Minecraft.getMinecraft().thePlayer;
    }

    public static boolean isMoving() {
        EntityPlayerSP player = player();
        return player != null && (player.movementInput.moveForward != 0.0f || player.movementInput.moveStrafe != 0.0f);
    }

    /** Sprint speed with the player's Speed effect applied. */
    public static double baseSpeed() {
        double base = SPRINT_SPEED;
        EntityPlayerSP player = player();
        if (player != null && player.isPotionActive(Potion.moveSpeed)) {
            base *= 1.0 + 0.2 * (player.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
        }
        return base;
    }

    public static double horizontalSpeed() {
        EntityPlayerSP player = player();
        return Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    /** The direction the held movement keys point, in radians, relative to the camera. */
    public static double direction() {
        EntityPlayerSP player = player();
        float yaw = player.rotationYaw;
        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        if (forward < 0.0f) {
            yaw += 180.0f;
        }
        float multiplier = forward < 0.0f ? -0.5f : (forward > 0.0f ? 0.5f : 1.0f);
        if (strafe > 0.0f) {
            yaw -= 90.0f * multiplier;
        } else if (strafe < 0.0f) {
            yaw += 90.0f * multiplier;
        }
        return Math.toRadians(yaw);
    }

    /** Moves at this speed in the held direction this tick, or stops if no key is held. */
    public static void setSpeed(MoveEvent event, double speed) {
        EntityPlayerSP player = player();
        if (!isMoving()) {
            event.setX(0.0);
            event.setZ(0.0);
            player.motionX = 0.0;
            player.motionZ = 0.0;
            return;
        }
        double direction = direction();
        double x = -Math.sin(direction) * speed;
        double z = Math.cos(direction) * speed;
        event.setX(x);
        event.setZ(z);
        player.motionX = x;
        player.motionZ = z;
    }

    /** Sets the player's motion toward the held direction without an event, for tick handlers. */
    public static void setMotion(double speed) {
        EntityPlayerSP player = player();
        if (!isMoving()) {
            player.motionX = 0.0;
            player.motionZ = 0.0;
            return;
        }
        double direction = direction();
        player.motionX = -Math.sin(direction) * speed;
        player.motionZ = Math.cos(direction) * speed;
    }
}
