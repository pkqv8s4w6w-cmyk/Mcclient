package dev.vantage.detect;

import java.util.Arrays;

/**
 * Measures how far away a player was when they landed a hit.
 *
 * <p>Only hits taken by the local player are measured. For an attack between two other players the
 * client sees neither the attack packet nor the positions the server used, so any figure it
 * produced would be guesswork dressed up as a measurement.
 *
 * <p>Distance is from the attacker's eyes to the nearest point of the victim's hitbox, which is
 * what the server itself checks. Measuring centre to centre instead would read about half a block
 * long for everyone and flag the whole lobby.
 */
public final class ReachAnalysis {

    /** Vanilla attack range for players. */
    public static final double VANILLA_REACH = 3.0;

    public static final class Result {
        private final int samples;
        private final double median;
        private final double worst;
        private final double confidence;

        Result(int samples, double median, double worst, double confidence) {
            this.samples = samples;
            this.median = median;
            this.worst = worst;
            this.confidence = confidence;
        }

        public int getSamples() {
            return samples;
        }

        public double getMedian() {
            return median;
        }

        public double getWorst() {
            return worst;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0.0, 0.0, 0.0);

    private static final int MIN_SAMPLES = 5;

    private ReachAnalysis() {
    }

    /**
     * Straight-line distance from a point to the closest point of a box. Returns zero when the
     * point is inside it.
     */
    public static double distanceToBox(double x, double y, double z,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double dx = Math.max(minX - x, Math.max(0.0, x - maxX));
        double dy = Math.max(minY - y, Math.max(0.0, y - maxY));
        double dz = Math.max(minZ - z, Math.max(0.0, z - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * @param distances    measured reach for each landed hit
     * @param allowedReach distance to treat as legitimate. Sits above vanilla's 3.0 because the
     *                     server rewinds positions to compensate for latency, so an honest hit
     *                     routinely measures long on the receiving client.
     */
    public static Result analyse(double[] distances, double allowedReach) {
        if (distances == null || distances.length < MIN_SAMPLES) {
            return NOTHING;
        }
        double[] sorted = Arrays.copyOf(distances, distances.length);
        Arrays.sort(sorted);

        // The median, not the maximum: one long reading is a lag spike, a shifted middle is not.
        double median = sorted.length % 2 == 1
                ? sorted[sorted.length / 2]
                : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
        double worst = sorted[sorted.length - 1];

        if (median <= allowedReach) {
            return new Result(distances.length, median, worst, 0.0);
        }
        // Half a block past the allowance is as confident as this gets.
        double confidence = Math.min(1.0, (median - allowedReach) / 0.5);
        return new Result(distances.length, median, worst, confidence);
    }
}
