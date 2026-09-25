package dev.vantage.detect;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A suspicion score that fades, and that will not convict on one witness.
 *
 * <p>Two rules, and the second is the one that matters.
 *
 * <p><b>It fades.</b> One odd reading means nothing; a pattern over a minute means something. Decay
 * is exponential against wall time rather than a fixed number of ticks, so a player who trips a
 * check once and then plays normally drops back to clean instead of carrying it for the game.
 *
 * <p><b>It needs corroborating.</b> Reaching the threshold is not enough on its own. Either two
 * different checks have to agree, or a single check has to have said the same thing across several
 * separate windows of evidence. Every check here is a heuristic reading indirect data, and each one
 * has some rate of being wrong about somebody honest; requiring independent agreement is what turns
 * several unreliable signals into one that can be trusted. Without it, the detector was one bad
 * reading away from accusing anybody, and it duly accused nearly everybody.
 */
public final class ViolationAccumulator {

    /** Separate windows a lone check has to fire across before it counts on its own. */
    private static final int REPEATS_FOR_A_LONE_CHECK = 3;

    /** How much of a check's score has to survive for it to still count toward corroboration. */
    private static final double LIVE_SCORE_FLOOR = 0.5;

    /** One check's contribution, and how many separate times it has spoken up. */
    private static final class Strand {
        double score;
        int sightings;
    }

    private final double halfLifeMillis;
    private final double notifyAt;

    private final Map<String, Strand> strands = new LinkedHashMap<String, Strand>();
    private long lastUpdate;
    // Separate from lastUpdate rather than using zero as a sentinel, because zero is a perfectly
    // ordinary timestamp and treating it as "never started" silently skips the first decay.
    private boolean started;
    private boolean notified;

    /**
     * @param halfLifeMillis how long until an untouched score halves
     * @param notifyAt       score at which this is worth telling the player about
     */
    public ViolationAccumulator(double halfLifeMillis, double notifyAt) {
        this.halfLifeMillis = Math.max(1.0, halfLifeMillis);
        this.notifyAt = notifyAt;
    }

    /**
     * Records one check reaching a verdict.
     *
     * @param check  which check spoke, so agreement between different ones can be recognised
     * @param amount how strongly, never negative
     */
    public void add(String check, double amount, long nowMillis) {
        decayTo(nowMillis);
        if (amount <= 0.0) {
            return;
        }
        Strand strand = strands.get(check);
        if (strand == null) {
            strand = new Strand();
            strands.put(check, strand);
        }
        strand.score += amount;
        strand.sightings++;
    }

    public double get(long nowMillis) {
        decayTo(nowMillis);
        double total = 0.0;
        for (Strand strand : strands.values()) {
            total += strand.score;
        }
        return total;
    }

    /** How many different checks currently have something to say. */
    public int liveChecks(long nowMillis) {
        decayTo(nowMillis);
        int live = 0;
        for (Strand strand : strands.values()) {
            if (strand.score >= LIVE_SCORE_FLOOR) {
                live++;
            }
        }
        return live;
    }

    /** The check contributing the most right now, or null when nothing is. */
    public String strongestCheck(long nowMillis) {
        decayTo(nowMillis);
        String best = null;
        double bestScore = 0.0;
        for (Map.Entry<String, Strand> entry : strands.entrySet()) {
            if (entry.getValue().score > bestScore) {
                bestScore = entry.getValue().score;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Whether the evidence is strong enough <em>and</em> independent enough to act on.
     *
     * <p>A single check repeating itself counts only once it has fired across several separate
     * windows, because by then it is describing behaviour that persisted rather than one stretch of
     * play that happened to look odd.
     */
    public boolean isOverThreshold(long nowMillis) {
        return get(nowMillis) >= notifyAt && isCorroborated(nowMillis);
    }

    private boolean isCorroborated(long nowMillis) {
        if (liveChecks(nowMillis) >= 2) {
            return true;
        }
        for (Strand strand : strands.values()) {
            if (strand.score >= LIVE_SCORE_FLOOR && strand.sightings >= REPEATS_FOR_A_LONE_CHECK) {
                return true;
            }
        }
        return false;
    }

    /**
     * True the first time the evidence crosses the bar, and not again until it has faded away.
     * That is what keeps one cheating player from filling the chat.
     */
    public boolean shouldNotify(long nowMillis) {
        if (notified || !isOverThreshold(nowMillis)) {
            return false;
        }
        notified = true;
        return true;
    }

    private void decayTo(long nowMillis) {
        if (!started) {
            started = true;
            lastUpdate = nowMillis;
            return;
        }
        long elapsed = nowMillis - lastUpdate;
        if (elapsed <= 0L) {
            return;
        }
        lastUpdate = nowMillis;

        double factor = Math.pow(0.5, elapsed / halfLifeMillis);
        boolean anythingLeft = false;
        for (Strand strand : strands.values()) {
            strand.score *= factor;
            if (strand.score < 0.001) {
                strand.score = 0.0;
                // A check that has fully faded starts over, so an old sighting cannot combine with
                // a new one months of play later to convict on evidence that no longer exists.
                strand.sightings = 0;
            } else {
                anythingLeft = true;
            }
        }
        if (!anythingLeft) {
            // Once everything has faded the player starts clean, including their notification.
            notified = false;
        }
    }

    public void reset() {
        strands.clear();
        lastUpdate = 0L;
        started = false;
        notified = false;
    }
}
