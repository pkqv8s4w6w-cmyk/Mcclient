package dev.vantage.threat;

/** A scored player, ready to be drawn. Ordered so the biggest threat sorts first. */
public final class ThreatEntry implements Comparable<ThreatEntry> {

    private final ThreatInput input;
    private final double score;
    private final double statsScore;
    private final double gearScore;
    private final double momentumScore;
    private final boolean statsCounted;

    ThreatEntry(ThreatInput input, double score, double statsScore, double gearScore,
                double momentumScore, boolean statsCounted) {
        this.input = input;
        this.score = score;
        this.statsScore = statsScore;
        this.gearScore = gearScore;
        this.momentumScore = momentumScore;
        this.statsCounted = statsCounted;
    }

    public ThreatInput getInput() {
        return input;
    }

    public String getName() {
        return input.getName();
    }

    public String getTeam() {
        return input.getTeam();
    }

    /** The overall 0-10 rating. */
    public double getScore() {
        return score;
    }

    public double getStatsScore() {
        return statsScore;
    }

    public double getGearScore() {
        return gearScore;
    }

    public double getMomentumScore() {
        return momentumScore;
    }

    /** False when the player's stats were unavailable and the score rests on gear alone. */
    public boolean isStatsCounted() {
        return statsCounted;
    }

    @Override
    public int compareTo(ThreatEntry other) {
        // Highest score first; ties broken by name so the list does not shuffle between frames.
        int byScore = Double.compare(other.score, this.score);
        return byScore != 0 ? byScore : getName().compareToIgnoreCase(other.getName());
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%s %.1f", getName(), score);
    }
}
