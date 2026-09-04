package dev.vantage.threat;

/**
 * How much each factor counts toward the final score.
 *
 * <p>Exposed as settings so the balance can be tuned in game. Values are relative, not required to
 * sum to anything; the engine normalises them, which also means it can drop a factor entirely when
 * the data is missing without the remaining score collapsing.
 */
public final class ThreatWeights {

    public static final ThreatWeights DEFAULT = new ThreatWeights(0.45, 0.35, 0.20);

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
