package dev.vantage.detect;

/**
 * One tick of a player's movement, as the client observed it.
 *
 * <p>Plain data with no Minecraft types so the movement checks can be tested directly.
 *
 * <p>{@code teleported} marks a sample the server moved rather than the player: positions arrive
 * quantised and a teleport resets them outright, so any check that differences two positions has
 * to skip these. Not doing so is the largest source of false positives in movement detection,
 * ahead of latency.
 */
public final class MovementSample {

    public final double x;
    public final double y;
    public final double z;
    public final boolean onGround;
    public final boolean sprinting;

    /** Dot product of their horizontal facing and their horizontal movement, -1 to 1. */
    public final double facingDot;

    public final boolean teleported;

    public MovementSample(double x, double y, double z, boolean onGround, boolean sprinting,
                          double facingDot, boolean teleported) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.onGround = onGround;
        this.sprinting = sprinting;
        this.facingDot = facingDot;
        this.teleported = teleported;
    }

    public double horizontalDistanceTo(MovementSample other) {
        double dx = other.x - x;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
