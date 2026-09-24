package dev.vantage.event;

/**
 * The movement packet the player is about to send, and again once it has gone.
 *
 * <p>At {@link Stage#PRE} the rotation and ground state are what the server will be told, so a
 * handler that changes them changes what the server sees without touching the camera. Position is
 * read-only: moving the player belongs in {@link MoveEvent}, where collision still applies.
 *
 * <p>At {@link Stage#POST} the packet has been sent and the values are the ones that went out.
 */
public final class MotionEvent {

    private final Stage stage;
    private final double x;
    private final double y;
    private final double z;
    private float yaw;
    private float pitch;
    private boolean onGround;
    private boolean rotationChanged;

    public MotionEvent(Stage stage, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.stage = stage;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    public Stage getStage() {
        return stage;
    }

    public boolean isPre() {
        return stage == Stage.PRE;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.rotationChanged = true;
    }

    public boolean isRotationChanged() {
        return rotationChanged;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }
}
