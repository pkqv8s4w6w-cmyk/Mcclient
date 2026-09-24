package dev.vantage.threat;

/** A scored player, ready to be drawn. Ordered so the biggest threat sorts first. */
public final class ThreatEntry implements Comparable<ThreatEntry> {

    private final ThreatInput input;
    private final double score;
    private final double skillScore;
    private final double starBonus;
    private final double gearModifier;
    private final boolean statsCounted;
    private final boolean lowSample;

    ThreatEntry(ThreatInput input, double score, double skillScore, double starBonus,
                double gearModifier, boolean statsCounted, boolean lowSample) {
        this.input = input;
        this.score = score;
        this.skillScore = skillScore;
        this.starBonus = starBonus;
        this.gearModifier = gearModifier;
        this.statsCounted = statsCounted;
        this.lowSample = lowSample;
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

    /** The part that came from their lifetime ratios, before star and gear. */
    public double getSkillScore() {
        return skillScore;
    }

    public double getStarBonus() {
        return starBonus;
    }

    /** How much their visible loadout moved the score, positive or negative. */
    public double getGearModifier() {
        return gearModifier;
    }

    /** False when the player's stats were unavailable, so the score rests on very little. */
    public boolean isStatsCounted() {
        return statsCounted;
    }

    /**
     * True when their record is too thin for the ratios to mean much, so the score was pulled
     * toward the middle. Worth marking in the list: the number is a guess, not a measurement.
     */
    public boolean isLowSample() {
        return lowSample;
    }

    public boolean isSelf() {
        return input.isSelf();
    }

    public boolean isFlaggedForCheating() {
        return input.isFlaggedForCheating();
    }

    @Override
    public int compareTo(ThreatEntry other) {
        // Anyone caught cheating sorts above everyone, rather than relying on the score floor to
        // out-argue a genuinely elite player in full diamond. It would, sometimes, and that is not
        // a property worth depending on.
        if (isFlaggedForCheating() != other.isFlaggedForCheating()) {
            return isFlaggedForCheating() ? -1 : 1;
        }
        // Then highest score, with ties broken by name so the list does not shuffle each frame.
        int byScore = Double.compare(other.score, this.score);
        return byScore != 0 ? byScore : getName().compareToIgnoreCase(other.getName());
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%s %.1f", getName(), score);
    }
}
