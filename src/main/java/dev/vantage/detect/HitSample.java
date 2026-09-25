package dev.vantage.detect;

/**
 * One blow somebody landed on you, measured two ways.
 *
 * <p>Only hits you take are recorded. For an attack between two other players the client sees
 * neither the attack packet nor the positions the server used, so any figure it produced would be
 * guesswork dressed up as a measurement.
 *
 * <p>Two distances, because the difference between them is what separates the two cheats that both
 * look like "they hit me from too far away":
 *
 * <ul>
 *   <li>{@link #getDistanceNow()} — how far they were when the damage arrived.
 *   <li>{@link #getBestDistance()} — the closest they came at any point in the last couple of
 *       seconds, and {@link #getBestTicksAgo()} says when.
 * </ul>
 *
 * <p>Both large means the hit was never legal from any position: that is reach. Only the first
 * large means the hit was legal a moment ago, which is either an honest laggy connection or
 * somebody making their connection look laggy on purpose. Telling those two apart is
 * {@link BacktrackAnalysis}'s job.
 */
public final class HitSample {

    private final double distanceNow;
    private final double bestDistance;
    private final int bestTicksAgo;

    public HitSample(double distanceNow, double bestDistance, int bestTicksAgo) {
        this.distanceNow = distanceNow;
        this.bestDistance = bestDistance;
        this.bestTicksAgo = Math.max(0, bestTicksAgo);
    }

    public double getDistanceNow() {
        return distanceNow;
    }

    public double getBestDistance() {
        return bestDistance;
    }

    public int getBestTicksAgo() {
        return bestTicksAgo;
    }
}
