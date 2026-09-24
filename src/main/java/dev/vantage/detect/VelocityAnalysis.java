package dev.vantage.detect;

import java.util.Arrays;

/**
 * Looks for reduced knockback.
 *
 * <p>A hit shoves you, and how far you travel afterwards follows from the game's physics. Someone
 * who barely moves after being hit repeatedly is cancelling that.
 *
 * <p>Judged on the median across several hits rather than any one. A player hit into a wall, or
 * hurt by something that imparts no knockback at all such as a fall or poison, will legitimately
 * barely move, and any of those on their own would otherwise convict.
 */
public final class VelocityAnalysis {

    public static final class Result {
        private final int hits;
        private final double medianDisplacement;
        private final double confidence;

        Result(int hits, double medianDisplacement, double confidence) {
            this.hits = hits;
            this.medianDisplacement = medianDisplacement;
            this.confidence = confidence;
        }

        public int getHits() {
            return hits;
        }

        public double getMedianDisplacement() {
            return medianDisplacement;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0.0, 0.0);

    private static final int MIN_HITS = 4;

    private VelocityAnalysis() {
    }

    /**
     * @param displacements horizontal distance travelled in the ticks after each hit, in blocks
     * @param expected      the least a normal hit should move someone
     */
    public static Result analyse(double[] displacements, double expected) {
        if (displacements == null || displacements.length < MIN_HITS) {
            return NOTHING;
        }
        double[] sorted = Arrays.copyOf(displacements, displacements.length);
        Arrays.sort(sorted);
        double median = sorted.length % 2 == 1
                ? sorted[sorted.length / 2]
                : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;

        if (median >= expected) {
            return new Result(displacements.length, median, 0.0);
        }
        // Taking no knockback at all is as confident as this gets.
        double confidence = Math.min(1.0, (expected - median) / expected);
        return new Result(displacements.length, median, confidence);
    }
}
