package dev.vantage.threat;

/**
 * How much each factor counts toward the final score.
 *
 * <p>Values are relative rather than required to sum to anything: the engine normalises whichever
 * factors it could actually observe, which is what lets it drop one entirely without the remaining
 * score collapsing.
 *
 * <p>The momentum weight is retained for compatibility with saved profiles but no longer blended —
 * bed state and current form are applied as a bounded modifier instead, so they adjust a score
 * rather than dominating one.
 */
public final class ThreatWeights {

    public static final ThreatWeights DEFAULT = new ThreatWeights(0.55, 0.30, 0.0);

    private final double stats;
    private final double gear;
    private final double momentum;

    public ThreatWeights(double stats, double gear, double momentum) {
        this.stats = Math.max(0.0, stats);
        this.gear = Math.max(0.0, gear);
        this.momentum = Math.max(0.0, momentum);
    }

    public double getStats() {
        return stats;
    }

    public double getGear() {
        return gear;
    }

    public double getMomentum() {
        return momentum;
    }

    public double sum() {
        return stats + gear + momentum;
    }
}
