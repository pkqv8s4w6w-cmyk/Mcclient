package dev.vantage.detect;

/**
 * Looks for automated clicking in a player's swing timings.
 *
 * <p>The tell is not a high click rate. People reach 12 to 16 clicks a second by butterfly or drag
 * clicking, and that is legitimate. What no hand produces is <em>consistency</em>: human intervals
 * scatter by tens of milliseconds, while an autoclicker fires on a timer and the spread collapses.
 * So this measures the standard deviation of the gaps between clicks, not the rate.
 *
 * <p>Swing packets are also sent for mining and placing, which is why a verdict needs a sustained
 * run of clicks rather than a brief burst.
 */
public final class ClickAnalysis {

    /** What the timings looked like, kept so a notification can quote real numbers. */
    public static final class Result {
        private final int samples;
        private final double clicksPerSecond;
        private final double standardDeviationMillis;
        private final double confidence;

        Result(int samples, double clicksPerSecond, double standardDeviationMillis, double confidence) {
            this.samples = samples;
            this.clicksPerSecond = clicksPerSecond;
            this.standardDeviationMillis = standardDeviationMillis;
            this.confidence = confidence;
        }

        public int getSamples() {
            return samples;
        }

        public double getClicksPerSecond() {
            return clicksPerSecond;
        }

        public double getStandardDeviationMillis() {
            return standardDeviationMillis;
        }

        /** 0 to 1. Zero means nothing to report. */
        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0.0, 0.0, 0.0);

    /** Below this many clicks there is not enough signal to say anything. */
    private static final int MIN_SAMPLES = 12;

    /** Slower than this and the gaps are dominated by deliberate pauses, not clicking. */
    private static final double MIN_CLICKS_PER_SECOND = 6.0;

    private ClickAnalysis() {
    }

    /**
     * @param clickTimesMillis swing timestamps, oldest first
     * @param maxSpreadMillis  standard deviation below which the timing is machine-like. Human
     *                         click jitter has a roughly fixed floor in absolute terms - fast
     *                         clicking is tighter than slow clicking, but not by much - so this is
     *                         an absolute figure rather than a share of the interval. Network
     *                         jitter only ever inflates the measured spread, so a laggy connection
     *                         causes missed detections rather than false ones; raising this to
     *                         compensate would start catching fast legitimate clicking instead.
     */
    public static Result analyse(long[] clickTimesMillis, double maxSpreadMillis) {
        if (clickTimesMillis == null || clickTimesMillis.length < MIN_SAMPLES + 1) {
            return NOTHING;
        }

        double[] intervals = new double[clickTimesMillis.length - 1];
        for (int i = 1; i < clickTimesMillis.length; i++) {
            double gap = clickTimesMillis[i] - clickTimesMillis[i - 1];
            if (gap <= 0) {
                // Two swings in the same millisecond; the sequence is unusable as timing data.
                return NOTHING;
            }
            intervals[i - 1] = gap;
        }

        double mean = mean(intervals);
        if (mean <= 0.0) {
            return NOTHING;
        }
        double clicksPerSecond = 1000.0 / mean;
        if (clicksPerSecond < MIN_CLICKS_PER_SECOND) {
            return NOTHING;
        }

        double deviation = standardDeviation(intervals, mean);
        if (deviation >= maxSpreadMillis) {
            return new Result(intervals.length, clicksPerSecond, deviation, 0.0);
        }

        // The further below the threshold, the more confident. Perfectly even clicking is a 1.
        double confidence = Math.min(1.0, 1.0 - (deviation / Math.max(1e-9, maxSpreadMillis)));
        return new Result(intervals.length, clicksPerSecond, deviation, confidence);
    }

    private static double mean(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static double standardDeviation(double[] values, double mean) {
        double sum = 0.0;
        for (double value : values) {
            double difference = value - mean;
            sum += difference * difference;
        }
        return Math.sqrt(sum / values.length);
    }
}
