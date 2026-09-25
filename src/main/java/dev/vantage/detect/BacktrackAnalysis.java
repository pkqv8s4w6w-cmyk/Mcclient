package dev.vantage.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Looks for somebody hitting where you used to be.
 *
 * <p>Backtrack works by holding onto the position packets a client receives instead of applying
 * them, so the cheater's game keeps drawing you where you were a fraction of a second ago and they
 * attack that. The server's own lag compensation then accepts the hit, because as far as it can
 * tell they simply have a slow connection. It is built to look exactly like latency, and on the
 * attacker's side there is nothing to see: their outgoing packets never reach you.
 *
 * <p>But it is plainly visible from the receiving end, which is where this runs. A backtracked hit
 * lands when you are too far away to be hit, yet you <em>were</em> in range a moment earlier. That
 * on its own is not proof, because an honest player on a bad connection produces the same thing.
 *
 * <p>The discriminator is <b>consistency</b>. Real latency wanders: the delay behind each hit
 * scatters across a wide range, because it is made of jitter, dropped packets and server tick
 * timing. A backtrack module holds packets for a configured length of time, so every hit lands at
 * close to the same delay. So the tell is not that the delays are large — it is that they are all
 * the same size.
 *
 * <p>No Minecraft references, so all of this is tested directly.
 */
public final class BacktrackAnalysis {

    public static final class Result {
        private final int hits;
        private final double medianTicksAgo;
        private final double spreadTicks;
        private final double confidence;

        Result(int hits, double medianTicksAgo, double spreadTicks, double confidence) {
            this.hits = hits;
            this.medianTicksAgo = medianTicksAgo;
            this.spreadTicks = spreadTicks;
            this.confidence = confidence;
        }

        /** How many hits carried the signature. */
        public int getHits() {
            return hits;
        }

        /** How far back the hits were being taken from, in ticks. */
        public double getMedianTicksAgo() {
            return medianTicksAgo;
        }

        /** How much that delay varied. Small is the suspicious direction. */
        public double getSpreadTicks() {
            return spreadTicks;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0.0, 0.0, 0.0);

    /** Fewer landed hits than this and a run of bad luck on a bad connection looks like a pattern. */
    private static final int MIN_HITS = 10;

    /**
     * How far back a hit has to be taken from before it means anything.
     *
     * <p>A tick or two behind is ordinary interpolation and ordinary ping. Backtrack modules are
     * configured in the hundreds of milliseconds, because anything less does not buy enough room to
     * be worth running.
     */
    private static final int MIN_TICKS_AGO = 4;

    /** Standard deviation of the delay, in ticks, below which it stops looking like a network. */
    private static final double MACHINE_SPREAD_TICKS = 1.6;

    /** Share of the over-reach hits that have to carry the signature. */
    private static final double MIN_SIGNATURE_SHARE = 0.7;

    private BacktrackAnalysis() {
    }

    /**
     * @param hits         every blow they have landed on you recently
     * @param allowedReach the distance a hit is allowed to measure without needing an explanation
     */
    public static Result analyse(List<HitSample> hits, double allowedReach) {
        if (hits == null || hits.size() < MIN_HITS) {
            return NOTHING;
        }

        // Only hits that need explaining are interesting. A hit that landed at arm's length says
        // nothing about anybody's packet timing.
        int needExplaining = 0;
        List<Double> delays = new ArrayList<Double>();
        for (HitSample hit : hits) {
            if (hit.getDistanceNow() <= allowedReach) {
                continue;
            }
            needExplaining++;
            // The hit was legal against where you were, just not against where you are. That is
            // the shape backtrack leaves; a plain reach cheat leaves both distances long.
            if (hit.getBestDistance() <= allowedReach && hit.getBestTicksAgo() >= MIN_TICKS_AGO) {
                delays.add((double) hit.getBestTicksAgo());
            }
        }

        if (needExplaining < MIN_HITS || delays.size() < MIN_HITS) {
            return NOTHING;
        }
        double share = delays.size() / (double) needExplaining;
        if (share < MIN_SIGNATURE_SHARE) {
            return NOTHING;
        }

        double median = median(delays);
        double spread = standardDeviation(delays);

        if (spread >= MACHINE_SPREAD_TICKS) {
            // The delays wander, which is what a real connection does. Report the numbers so a
            // laggy player can be told apart from a clean one, but do not accuse anybody.
            return new Result(delays.size(), median, spread, 0.0);
        }

        // The tighter the delays cluster, the less it looks like a network and the more it looks
        // like a setting.
        double confidence = Math.min(1.0, 1.0 - (spread / MACHINE_SPREAD_TICKS));
        return new Result(delays.size(), median, spread, confidence);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        return size % 2 == 1
                ? sorted.get(size / 2)
                : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    private static double standardDeviation(List<Double> values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        double mean = total / values.size();

        double sum = 0.0;
        for (double value : values) {
            double difference = value - mean;
            sum += difference * difference;
        }
        return Math.sqrt(sum / values.size());
    }
}
