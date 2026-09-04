package dev.vantage.threat;

import dev.vantage.hypixel.BedwarsStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns what is known about a player into a single 0-10 rating.
 *
 * <p>Three factors are scored independently and then blended: lifetime stats, the gear they are
 * carrying right now, and how the current game is going for them. Keeping them separate means the
 * list can explain itself, and means a missing factor can be dropped without the rest collapsing.
 *
 * <p>No Minecraft references, so the scoring can be tested directly.
 */
public final class ThreatEngine {

    /** The FKDR that scores a full 10 on the stats factor. */
    private static final double FKDR_CEILING = 20.0;

    /** The Bedwars level that scores a full 10 on the stats factor. */
    private static final double STAR_CEILING = 600.0;

    /**
     * Final kills plus final deaths needed before a ratio is taken at face value. Below this the
     * score is pulled toward {@link #NEUTRAL_SCORE}, because a player who is 5 and 0 is not
     * actually a threat, they have just not died yet.
     */
    private static final double CONFIDENCE_SAMPLE = 40.0;

    private static final double NEUTRAL_SCORE = 2.5;

    private ThreatEngine() {
    }

    public static ThreatEntry evaluate(ThreatInput input) {
        return evaluate(input, ThreatWeights.DEFAULT);
    }

    public static ThreatEntry evaluate(ThreatInput input, ThreatWeights weights) {
        BedwarsStats stats = input.getStats();
        boolean statsCounted = !stats.isUnknown();

        double statsScore = statsCounted ? scoreStats(stats) : 0.0;
        double gearScore = scoreGear(input.getGear());
        double momentumScore = scoreMomentum(input);

        double statsWeight = statsCounted ? weights.getStats() : 0.0;
        double totalWeight = statsWeight + weights.getGear() + weights.getMomentum();

        double score;
        if (totalWeight <= 0.0) {
            // Every weight zeroed. Fall back to a flat average rather than dividing by zero.
            score = statsCounted ? (statsScore + gearScore + momentumScore) / 3.0
                    : (gearScore + momentumScore) / 2.0;
        } else {
            score = (statsScore * statsWeight
                    + gearScore * weights.getGear()
                    + momentumScore * weights.getMomentum()) / totalWeight;
        }

        return new ThreatEntry(input, clamp(score), statsScore, gearScore, momentumScore, statsCounted);
    }

    /**
     * Blends final kill/death ratio with Bedwars level.
     *
     * <p>Both are log-scaled: the gap between 2 and 6 FKDR matters far more than the gap between
     * 20 and 40, and a linear scale would put almost every real player in the bottom fifth of the
     * range. Thin records are shrunk toward a neutral score in proportion to how thin they are.
     */
    private static double scoreStats(BedwarsStats stats) {
        double fkdrPart = 10.0 * Math.log1p(Math.max(0.0, stats.getFinalKillDeathRatio()))
                / Math.log1p(FKDR_CEILING);
        double starPart = 10.0 * Math.log1p(Math.max(0, stats.getStar())) / Math.log1p(STAR_CEILING);
        double raw = clamp(0.70 * fkdrPart + 0.30 * starPart);

        double sample = stats.getFinalKills() + stats.getFinalDeaths();
        double confidence = Math.min(1.0, sample / CONFIDENCE_SAMPLE);
        return NEUTRAL_SCORE + (raw - NEUTRAL_SCORE) * confidence;
    }

    /** Armour and weapon tier, each nudged by its enchantment level. */
    private static double scoreGear(Gear gear) {
        double armour = gear.getArmour().getPoints() + Math.min(1.0, gear.getProtection() * 0.25);
        double weapon = gear.getWeapon().getPoints() + Math.min(1.5, gear.getSharpness() * 0.75);
        return clamp(0.55 * armour + 0.45 * weapon);
    }

    /**
     * How the current game is going for them.
     *
     * <p>An intact bed counts for a lot: it means every kill they take has to be taken again,
     * which is a longer-lived threat than a bedless player on the same stats.
     */
    private static double scoreMomentum(ThreatInput input) {
        double killPart = Math.min(6.0, input.getKillsThisGame() * 1.5);
        double bedPart = input.isBedIntact() ? 3.0 : 0.5;
        double deathPenalty = Math.min(2.0, input.getDeathsThisGame() * 0.5);
        return clamp(killPart + bedPart - deathPenalty);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return value < 0.0 ? 0.0 : (value > 10.0 ? 10.0 : value);
    }

    /** Scores a whole lobby, most dangerous first. Your own player is left out. */
    public static List<ThreatEntry> rank(List<ThreatInput> inputs, ThreatWeights weights) {
        List<ThreatEntry> entries = new ArrayList<ThreatEntry>(inputs.size());
        for (ThreatInput input : inputs) {
            if (input.isSelf()) {
                continue;
            }
            entries.add(evaluate(input, weights));
        }
        Collections.sort(entries);
        return entries;
    }
}
