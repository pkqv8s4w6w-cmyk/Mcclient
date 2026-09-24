package dev.vantage.event;

/**
 * Input is about to be turned into acceleration. The yaw is the direction "forward" means.
 *
 * <p>Silent rotations change what the server thinks the player is facing, while the keys still
 * mean what the camera faces. Handing the server yaw here keeps the movement consistent with the
 * rotation that was sent; leaving it alone keeps movement relative to the camera.
 */
public final class StrafeEvent extends Cancellable {

    private float strafe;
    private float forward;
    private float friction;
    private float yaw;

    public StrafeEvent(float strafe, float forward, float friction, float yaw) {
        this.strafe = strafe;
        this.forward = forward;
        this.friction = friction;
        this.yaw = yaw;
    }

    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}
