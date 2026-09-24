package dev.vantage.event;

/** The local player is jumping. The yaw decides which way a sprint-jump pushes. */
public final class JumpEvent extends Cancellable {

    private float motionY;
    private float yaw;

    public JumpEvent(float motionY, float yaw) {
        this.motionY = motionY;
        this.yaw = yaw;
    }

    public float getMotionY() {
        return motionY;
    }

    public void setMotionY(float motionY) {
        this.motionY = motionY;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}
