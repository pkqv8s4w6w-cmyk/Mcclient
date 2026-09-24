package dev.vantage.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Steps vanilla projectile and falling-player physics forward, tick by tick.
 *
 * <p>The constants and the order of operations are copied from the 1.8.9 entity update methods,
 * because a prediction that is off by the order of drag and gravity drifts by blocks over a long
 * throw. Collision is supplied by the caller, which keeps this class free of Minecraft and lets
 * the tests use a flat floor or open void.
 */
public final class Simulation {

    /** Answers whether a segment of a path hits something solid, and where. */
    public interface Collider {
        /**
         * @return the fraction 0..1 along the segment at which it first hits a block, or -1 if it
         * passes through freely
         */
        double hit(double x0, double y0, double z0, double x1, double y1, double z1);
    }

    /** Nothing to hit anywhere: the void. */
    public static final Collider OPEN_AIR = (x0, y0, z0, x1, y1, z1) -> -1.0;

    /** How a kind of projectile moves. */
    public enum Kind {
        /** Arrows: position, then drag 0.99, then gravity 0.05. */
        ARROW(0.05, 0.99, false),
        /** Ender pearls, snowballs and eggs: drag 0.99 then gravity 0.03. */
        THROWABLE(0.03, 0.99, false),
        /** Fireballs: no gravity; acceleration is added, then drag 0.95 applies. */
        FIREBALL(0.0, 0.95, true);

        final double gravity;
        final double drag;
        final boolean accelerates;

        Kind(double gravity, double drag, boolean accelerates) {
            this.gravity = gravity;
            this.drag = drag;
            this.accelerates = accelerates;
        }
    }

    /** Where a simulated path went and how it ended. */
    public static final class Path {
        public final List<double[]> points = new ArrayList<double[]>();
        /** The first point that touched a block, or null if the path never landed. */
        public double[] impact;
        /** Ticks until the impact, or until the simulation gave up. */
        public int ticks;
        /** True if the path fell below the floor of the world without hitting anything. */
        public boolean fellIntoVoid;
    }

    private Simulation() {
    }

    /**
     * Follows a projectile until it hits something, falls out of the world or runs out of time.
     *
     * @param ax acceleration per tick, only used by fireballs
     */
    public static Path projectile(Kind kind, double x, double y, double z,
                                  double vx, double vy, double vz,
                                  double ax, double ay, double az,
                                  int maxTicks, double voidY, Collider collider) {
        Path path = new Path();
        path.points.add(new double[]{x, y, z});
        for (int tick = 1; tick <= maxTicks; tick++) {
            double nx = x + vx;
            double ny = y + vy;
            double nz = z + vz;
            double fraction = collider.hit(x, y, z, nx, ny, nz);
            if (fraction >= 0.0) {
                double[] impact = {x + vx * fraction, y + vy * fraction, z + vz * fraction};
                path.points.add(impact);
                path.impact = impact;
                path.ticks = tick;
                return path;
            }
            x = nx;
            y = ny;
            z = nz;
            path.points.add(new double[]{x, y, z});
            if (kind.accelerates) {
                vx = (vx + ax) * kind.drag;
                vy = (vy + ay) * kind.drag;
                vz = (vz + az) * kind.drag;
            } else {
                vx *= kind.drag;
                vy *= kind.drag;
                vz *= kind.drag;
                vy -= kind.gravity;
            }
            if (y < voidY) {
                path.fellIntoVoid = true;
                path.ticks = tick;
                return path;
            }
        }
        path.ticks = maxTicks;
        return path;
    }

    /**
     * Follows a player with no input held: gravity 0.08 then drag 0.98 vertically, air drag 0.91
     * horizontally. That is where a knocked-back player ends up if they do nothing, which is the
     * question a clutch has to answer.
     */
    public static Path fallingPlayer(double x, double y, double z, double vx, double vy, double vz,
                                     int maxTicks, double voidY, Collider collider) {
        Path path = new Path();
        path.points.add(new double[]{x, y, z});
        for (int tick = 1; tick <= maxTicks; tick++) {
            double nx = x + vx;
            double ny = y + vy;
            double nz = z + vz;
            double fraction = collider.hit(x, y, z, nx, ny, nz);
            if (fraction >= 0.0) {
                double[] impact = {x + vx * fraction, y + vy * fraction, z + vz * fraction};
                path.points.add(impact);
                path.impact = impact;
                path.ticks = tick;
                return path;
            }
            x = nx;
            y = ny;
            z = nz;
            path.points.add(new double[]{x, y, z});
            vy = (vy - 0.08) * 0.98;
            vx *= 0.91;
            vz *= 0.91;
            if (y < voidY) {
                path.fellIntoVoid = true;
                path.ticks = tick;
                return path;
            }
        }
        path.ticks = maxTicks;
        return path;
    }

    /**
     * Launch velocity of a bow shot at full or partial draw, as {@code ItemBow.onPlayerStoppedUsing}
     * computes it, without the random spread.
     *
     * @param charge ticks the bow has been drawn
     */
    public static double bowVelocity(int charge) {
        double f = charge / 20.0;
        f = (f * f + f * 2.0) / 3.0;
        return Math.min(f, 1.0) * 3.0;
    }

    /** The unit vector a player at this rotation looks along. */
    public static double[] direction(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new double[]{
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
        };
    }
}
