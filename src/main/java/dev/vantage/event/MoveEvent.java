package dev.vantage.event;

/**
 * The local player is about to move by this much, before collision is applied.
 *
 * <p>Changing the values changes the move; collision, step-up and the rest still run on whatever
 * the handlers leave here, so a speed module cannot push the player through a wall by accident.
 */
public final class MoveEvent extends Cancellable {

    private double x;
    private double y;
    private double z;
    private boolean safeWalk;

    public MoveEvent(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    /** Stop at block edges the way sneaking does, without sneaking. */
    public boolean isSafeWalk() {
        return safeWalk;
    }

    public void setSafeWalk(boolean safeWalk) {
        this.safeWalk = safeWalk;
    }
}
