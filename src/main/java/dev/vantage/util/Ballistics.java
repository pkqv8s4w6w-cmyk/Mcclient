package dev.vantage.util;

/**
 * Solving for the pitch that lands a projectile at a given distance and height, using the same
 * drag and gravity as {@link Simulation}. Used by the bow aimbot and by pearl clutches.
 */
public final class Ballistics {

    private Ballistics() {
    }

    /**
     * The flattest pitch at which a projectile launched at {@code velocity} passes the target's
     * horizontal distance at its height.
     *
     * @return the pitch in Minecraft's convention (negative is up), or null if out of range
     */
    public static Float solvePitch(Simulation.Kind kind, double horizontal, double height, double velocity) {
        // Forty-five degrees up is close to the longest reach; if that falls short, nothing reaches.
        float low = -45.0f;
        float high = 80.0f;
        Double lobError = heightErrorAt(kind, low, horizontal, height, velocity);
        if (lobError == null || lobError < 0.0) {
            return null;
        }
        // Raising the pitch lowers the projectile, so the error falls toward the flat solution.
        for (int i = 0; i < 30; i++) {
            float middle = (low + high) / 2.0f;
            Double middleError = heightErrorAt(kind, middle, horizontal, height, velocity);
            if (middleError != null && middleError >= 0.0) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2.0f;
    }

    /** How far above the target height the projectile is when it reaches the distance. */
    static Double heightErrorAt(Simulation.Kind kind, float pitch, double horizontal, double height, double velocity) {
        double radians = Math.toRadians(pitch);
        double vx = Math.cos(radians) * velocity;
        double vy = -Math.sin(radians) * velocity;
        double x = 0.0;
        double y = 0.0;
        for (int tick = 0; tick < 300; tick++) {
            if (x + vx >= horizontal) {
                double fraction = vx <= 0.0 ? 0.0 : (horizontal - x) / vx;
                return (y + vy * fraction) - height;
            }
            x += vx;
            y += vy;
            vx *= kind.drag;
            vy = vy * kind.drag - kind.gravity;
            if (y < height - 256.0) {
                return null;
            }
        }
        return null;
    }
}
