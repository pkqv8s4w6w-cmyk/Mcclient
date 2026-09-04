package dev.vantage.detect;

/**
 * A suspicion score that fades.
 *
 * <p>One odd reading means nothing; a pattern over a minute means something. Decay is exponential
 * against wall time rather than a fixed number of ticks, so a player who triggers a check once and
 * then plays normally drops back to clean instead of carrying it for the rest of the game.
 */
public final class ViolationAccumulator {

    private final double halfLifeMillis;
    private final double notifyAt;

    private double score;
    private long lastUpdate;
    private boolean notified;

    /**
     * @param halfLifeMillis how long until an untouched score halves
     * @param notifyAt       score at which this is worth telling the player about
     */
    public ViolationAccumulator(double halfLifeMillis, double notifyAt) {
        this.halfLifeMillis = Math.max(1.0, halfLifeMillis);
        this.notifyAt = notifyAt;
    }

    public void add(double amount, long nowMillis) {
        decayTo(nowMillis);
        score += Math.max(0.0, amount);
    }

    public double get(long nowMillis) {
        decayTo(nowMillis);
        return score;
    }

    private void decayTo(long nowMillis) {
        if (lastUpdate == 0L) {
            lastUpdate = nowMillis;
            return;
        }
        long elapsed = nowMillis - lastUpdate;
        if (elapsed <= 0L) {
            return;
        }
        lastUpdate = nowMillis;
        score *= Math.pow(0.5, elapsed / halfLifeMillis);
        if (score < 0.001) {
            score = 0.0;
            // Once a score has fully faded the player starts clean, including their notification.
            notified = false;
        }
    }

    /**
     * True the first time the score crosses the threshold, and not again until it has faded away.
     * That is what keeps one cheating player from filling the chat.
     */
    public boolean shouldNotify(long nowMillis) {
        if (notified || get(nowMillis) < notifyAt) {
            return false;
        }
        notified = true;
        return true;
    }

    public boolean isOverThreshold(long nowMillis) {
        return get(nowMillis) >= notifyAt;
    }

    public void reset() {
        score = 0.0;
        lastUpdate = 0L;
        notified = false;
    }
}
