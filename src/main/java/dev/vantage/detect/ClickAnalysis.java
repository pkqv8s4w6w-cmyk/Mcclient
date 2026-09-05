package dev.vantage.detect;

import java.util.Arrays;

/**
 * Looks for automated clicking in a player's swing timings.
 *
 * <p>The tell is not a high click rate. People reach 12 to 16 clicks a second by butterfly or drag
 * clicking, and that is legitimate. What no hand produces is <em>consistency</em>: human intervals
 * scatter by tens of milliseconds, while a clicker fires on a timer and the spread collapses.
 *
 * <p>Two things make that measurable rather than merely true.
 *
 * <p>First, the spread is taken over the <b>middle</b> of the sample. One dropped packet stretches
 * a single interval into an outlier, and a raw standard deviation is dominated by it — which meant
 * the old check quietly stopped catching clickers on any connection that ever hiccupped. Trimming
 * the extremes measures the rhythm instead of the network.
 *
 * <p>Second, those discarded extremes are themselves evidence, so they are counted rather than
 * thrown away. A person clicking for ten seconds pauses, fumbles, or gets distracted at least once;
 * a timer never does. A sample with a tight middle <em>and</em> no ragged edges is not a hand.
 *
 * <p>None of this can tell a click from any other arm swing, because the packet does not say.
 * Mining a wool block sends the same swing at a steady rate, and Bedwars players mine constantly —
 * which is why the caller only feeds in swings thrown during a fight. See {@link CombatSession}.
 */
public final class ClickAnalysis {

    /** What the timings looked like, kept so a notification can quote real numbers. */
    public static final class Result {
        private final int samples;
        private final double clicksPerSecond;
        private final double standardDeviationMillis;
        private final double outlierShare;
        private final double confidence;

        Result(int samples, double clicksPerSecond, double standardDeviationMillis,
               double outlierShare, double confidence) {
            this.samples = samples;
            this.clicksPerSecond = clicksPerSecond;
            this.standardDeviationMillis = standardDeviationMillis;
            this.outlierShare = outlierShare;
            this.confidence = confidence;
        }

        public int getSamples() {
            return samples;
        }

        public double getClicksPerSecond() {
            return clicksPerSecond;
        }

        /** Spread of the middle of the sample, in milliseconds. */
        public double getStandardDeviationMillis() {
            return standardDeviationMillis;
        }

        /** Share of intervals far longer than the rhythm — the pauses a hand makes and a timer does not. */
        public double getOutlierShare() {
            return outlierShare;
        }

        /** 0 to 1. Zero means nothing to report. */
        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0.0, 0.0, 0.0, 0.0);

    /**
     * Intervals needed before a verdict.
     *
     * <p>Was twelve, which is under a second of clicking — short enough that a steady burst of
     * mining looked like a machine. Two full seconds of it is a different claim.
     */
    private static final int MIN_INTERVALS = 25;

    /** Slower than this and the gaps are dominated by deliberate pauses, not clicking. */
    private static final double MIN_CLICKS_PER_SECOND = 6.0;

    /**
     * Spread below which the timing stops looking like a hand, in milliseconds.
     *
     * <p>Deliberately tight. Clickers that randomise typically jitter by about a tenth of the
     * interval, which at a dozen clicks a second works out near five milliseconds of spread, so
     * this still catches them — while a fast human, who sits above ten even at their steadiest,
     * is left alone. Raising it to catch more would start catching good players instead, and
     * accusing good players is the failure that matters.
     */
    public static final double DEFAULT_MAX_SPREAD_MILLIS = 8.0;

    /** Fraction of the sample trimmed from each end before measuring the spread. */
    private static final double TRIM_SHARE = 0.1;

    /** An interval this many times the typical one is a pause rather than a click. */
    private static final double OUTLIER_FACTOR = 2.0;

    /** Above this share of pauses, somebody is driving it by hand. */
    private static final double MAX_OUTLIER_SHARE = 0.02;

    private ClickAnalysis() {
    }

    /**
     * @param clickTimesMillis swing timestamps, oldest first
     * @param maxSpreadMillis  standard deviation below which the timing is machine-like. Human
     *                         click jitter has a roughly fixed floor in absolute terms — fast
     *                         clicking is tighter than slow clicking, but not by much — so this is
     *                         an absolute figure rather than a share of the interval. Network
     *                         jitter only ever inflates the measured spread, so a laggy connection
     *                         causes missed detections rather than false ones.
     */
    public static Result analyse(long[] clickTimesMillis, double maxSpreadMillis) {
        if (clickTimesMillis == null || clickTimesMillis.length < MIN_INTERVALS + 1) {
            return NOTHING;
        }

        double[] intervals = intervalsOf(clickTimesMillis);
        if (intervals.length < MIN_INTERVALS) {
            return NOTHING;
        }

        double mean = mean(intervals);
        if (mean <= 0.0) {
            return NOTHING;
        }
        double clicksPerSecond = 1000.0 / mean;
        if (clicksPerSecond < MIN_CLICKS_PER_SECOND) {
            return NOTHING;
        }

        double[] middle = trimmed(intervals);
        double spread = standardDeviation(middle, mean(middle));
        double outlierShare = outlierShare(intervals, mean(middle));

        if (spread >= maxSpreadMillis || outlierShare > MAX_OUTLIER_SHARE) {
            // Either the rhythm is loose or it is interrupted. Both are what a hand looks like.
            return new Result(intervals.length, clicksPerSecond, spread, outlierShare, 0.0);
        }

        // The further below the threshold, the more confident. Perfectly even clicking is a 1.
        double confidence = Math.min(1.0, 1.0 - (spread / Math.max(1e-9, maxSpreadMillis)));
        return new Result(intervals.length, clicksPerSecond, spread, outlierShare, confidence);
    }

    /**
     * Gaps between consecutive swings.
     *
     * <p>A non-positive gap means two swings arrived in the same millisecond, which is a packet
     * artefact rather than a click. That one interval is dropped. The old version threw away the
     * whole window instead, so a single duplicated timestamp blinded the check for seconds at a
     * time — and a clicker fast enough to produce duplicates was exactly the one it should have
     * caught.
     */
    private static double[] intervalsOf(long[] times) {
        double[] gaps = new double[times.length - 1];
        int count = 0;
        for (int i = 1; i < times.length; i++) {
            double gap = times[i] - times[i - 1];
            if (gap > 0.0) {
                gaps[count++] = gap;
            }
        }
        return Arrays.copyOf(gaps, count);
    }

    /** The middle of the sample, with the longest and shortest intervals removed. */
    private static double[] trimmed(double[] intervals) {
        double[] sorted = Arrays.copyOf(intervals, intervals.length);
        Arrays.sort(sorted);
        int cut = (int) Math.floor(sorted.length * TRIM_SHARE);
        if (sorted.length - 2 * cut < MIN_INTERVALS / 2) {
            return sorted;
        }
        return Arrays.copyOfRange(sorted, cut, sorted.length - cut);
    }

    private static double outlierShare(double[] intervals, double typical) {
        if (typical <= 0.0) {
            return 0.0;
        }
        int outliers = 0;
        for (double interval : intervals) {
            if (interval > typical * OUTLIER_FACTOR) {
                outliers++;
            }
        }
        return outliers / (double) intervals.length;
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static double standardDeviation(double[] values, double mean) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            double difference = value - mean;
            sum += difference * difference;
        }
        return Math.sqrt(sum / values.length);
    }
}
