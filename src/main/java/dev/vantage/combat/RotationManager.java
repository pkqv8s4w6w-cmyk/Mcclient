package dev.vantage.combat;

import dev.vantage.event.EventBus;
import dev.vantage.event.JumpEvent;
import dev.vantage.event.MotionEvent;
import dev.vantage.event.StrafeEvent;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * The one place rotations the server sees are decided.
 *
 * <p>Modules ask for a rotation each tick with a priority; the highest ask wins and is turned toward
 * at the asker's speed. When nobody asks, the server rotation eases back to the camera rather than
 * snapping, so letting go of a target does not produce a turn no hand could make.
 *
 * <p>The camera never moves. What the server is told and what the player sees are kept apart,
 * which is what "silent" means. With movement correction on, input is re-expressed relative to the
 * server yaw so the player keeps moving the way the camera faces.
 */
public final class RotationManager {

    /** Runs after every module has had its say in the same event. */
    private static final int PRIORITY = -1000;

    private static final RotationManager INSTANCE = new RotationManager();

    public static RotationManager get() {
        return INSTANCE;
    }

    private float targetYaw;
    private float targetPitch;
    private float speed;
    private int requestPriority = Integer.MIN_VALUE;
    private boolean requestMoveFix;
    private boolean requested;

    private float serverYaw;
    private float serverPitch;
    private boolean active;
    private boolean moveFix;

    private RotationManager() {
    }

    public void install() {
        EventBus.global().subscribe(MotionEvent.class, PRIORITY, this::onMotion, this);
        EventBus.global().subscribe(StrafeEvent.class, PRIORITY, this::onStrafe, this);
        EventBus.global().subscribe(JumpEvent.class, PRIORITY, this::onJump, this);
    }

    /**
     * Asks for the server to be told this rotation this tick.
     *
     * @param speed   maximum degrees turned per tick on each axis; 180 or more is instant
     * @param moveFix keep movement relative to the camera while rotated
     */
    public void request(float yaw, float pitch, int priority, float speed, boolean moveFix) {
        if (requested && priority < requestPriority) {
            return;
        }
        this.targetYaw = yaw;
        this.targetPitch = RotationUtil.clampPitch(pitch);
        this.speed = speed;
        this.requestPriority = priority;
        this.requestMoveFix = moveFix;
        this.requested = true;
    }

    /** Whether the server currently has a rotation other than the camera's. */
    public boolean isActive() {
        return active;
    }

    public float getServerYaw() {
        return serverYaw;
    }

    public float getServerPitch() {
        return serverPitch;
    }

    private void onMotion(MotionEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        if (!event.isPre()) {
            serverYaw = event.getYaw();
            serverPitch = event.getPitch();
            if (active) {
                // Show the server rotation on your own model in third person.
                player.rotationYawHead = serverYaw;
                player.renderYawOffset = serverYaw;
            }
            return;
        }

        if (!active && !requested) {
            serverYaw = player.rotationYaw;
            serverPitch = player.rotationPitch;
            return;
        }

        float goalYaw;
        float goalPitch;
        float step;
        if (requested) {
            goalYaw = targetYaw;
            goalPitch = targetPitch;
            step = speed;
            moveFix = requestMoveFix;
        } else {
            // Nobody wants a rotation any more: ease back to where the camera is looking.
            goalYaw = player.rotationYaw;
            goalPitch = player.rotationPitch;
            step = Math.max(speed, 30.0f);
        }

        float from = active ? serverYaw : player.rotationYaw;
        float fromPitch = active ? serverPitch : player.rotationPitch;
        float[] next = RotationUtil.stepTowards(from, fromPitch, goalYaw, goalPitch, step, step);
        float sensitivity = Minecraft.getMinecraft().gameSettings.mouseSensitivity;
        float yaw = RotationUtil.snapToMouse(from, next[0], sensitivity);
        float pitch = RotationUtil.clampPitch(RotationUtil.snapToMouse(fromPitch, next[1], sensitivity));

        if (!requested && Math.abs(RotationUtil.yawDifference(yaw, player.rotationYaw)) < 1.0f
                && Math.abs(pitch - player.rotationPitch) < 1.0f) {
            active = false;
            moveFix = false;
            clearRequest();
            return;
        }

        // Keep the sent yaw close to the camera's unwrapped value so the server never sees a
        // 360 degree jump between two consecutive packets.
        yaw = player.rotationYaw + RotationUtil.yawDifference(player.rotationYaw, yaw);
        event.setRotation(yaw, pitch);
        active = true;
        clearRequest();
    }

    private void clearRequest() {
        requested = false;
        requestPriority = Integer.MIN_VALUE;
    }

    /**
     * Re-expresses movement input relative to the server yaw so the direction of travel is still
     * the one the camera shows. Without this, a player aiming silently behind them runs backwards.
     */
    private void onStrafe(StrafeEvent event) {
        if (!active || !moveFix) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        float offset = RotationUtil.yawDifference(serverYaw, player.rotationYaw);
        double radians = Math.toRadians(offset);
        float forward = event.getForward();
        float strafe = event.getStrafe();
        float rotatedForward = (float) (forward * Math.cos(radians) + strafe * Math.sin(radians));
        float rotatedStrafe = (float) (strafe * Math.cos(radians) - forward * Math.sin(radians));
        event.setForward(rotatedForward);
        event.setStrafe(rotatedStrafe);
        event.setYaw(serverYaw);
    }

    private void onJump(JumpEvent event) {
        if (active && moveFix) {
            // The sprint-jump boost follows the camera, since that is the way the player is going.
            event.setYaw(Minecraft.getMinecraft().thePlayer.rotationYaw);
        }
    }
}
