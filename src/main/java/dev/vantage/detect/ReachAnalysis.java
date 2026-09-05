package dev.vantage.detect;

import java.util.Arrays;
import java.util.List;

/**
 * Measures how far away a player was when they landed a hit on you.
 *
 * <p>Only hits taken by the local player are measured. For an attack between two other players the
 * client sees neither the attack packet nor the positions the server used, so any figure it
 * produced would be guesswork dressed up as a measurement.
 *
 * <p>The distance that counts is <b>the closest they came at any moment still on record</b>, not
 * the distance when the damage arrived. A server rewinds the world to compensate for latency
 * before deciding whether a hit lands, so on any real connection an honest hit routinely measures
 * long by the time the client sees it. Judging on the instantaneous figure asks a question the
 * server never asked, and it reads long for everybody — which is how a check like this ends up
 * accusing an entire lobby.
 *
 * <p>Asking whether the hit was legal against <em>any</em> recent position is the same question the
 * server asks. A player whose hits were never legal from anywhere is reaching. A player whose hits
 * were legal a moment ago is either lagging or backtracking, which is
 * {@link BacktrackAnalysis}'s problem rather than this one.
 *
 * <p>Distance is from the attacker's eyes to the nearest point of your hitbox, which is what the
 * server itself checks. Measuring centre to centre instead would read about half a block long for
 * everyone.
 */
public final class ReachAnalysis {

    /** Vanilla attack range for players. */
    public static final double VANILLA_REACH = 3.0;

    /**
     * What a hit is allowed to measure before it needs explaining.
     *
     * <p>Above vanilla's 3.0 on purpose. Even after rewinding, the client's copy of another
     * player's position is quantised to a thirty-second of a block and arrives a tick behind, so a
     * legitimate hit at the edge of range measures a little long here however carefully it is
     * computed. Server-side anticheats that see the real numbers settle around 3.01; a third party
     * working from interpolated packets has to leave more room than that or it convicts the honest.
     */
    public static final double DEFAULT_ALLOWED_REACH = 3.5;

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

        /** The middle hit's distance, after rewinding. */
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

    /**
     * Hits needed before a verdict.
     *
     * <p>Was five, which is a couple of seconds of one fight. Somebody catching you at the edge of
     * their range twice in a row while you both strafe is not a pattern, and treating it as one is
     * most of why the old check spoke up about everybody.
     */
    private static final int MIN_SAMPLES = 8;

    private ReachAnalysis() {
    }

    /**
     * @param hits         every blow they have landed on you recently
     * @param allowedReach the distance to treat as legitimate after rewinding
     */
    public static Result analyse(List<HitSample> hits, double allowedReach) {
        if (hits == null || hits.size() < MIN_SAMPLES) {
            return NOTHING;
        }
        double[] distances = new double[hits.size()];
        for (int i = 0; i < distances.length; i++) {
            // The rewound figure, not the instantaneous one: this is the question the server asks.
            distances[i] = hits.get(i).getBestDistance();
        }
        return analyse(distances, allowedReach);
    }

    /** The same judgement over already-rewound distances, one per landed hit. */
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
