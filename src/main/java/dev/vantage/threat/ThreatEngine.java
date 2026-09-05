package dev.vantage.threat;

import dev.vantage.hypixel.BedwarsStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns what is known about a player into a single 0-10 rating.
 *
 * <p>The scale is anchored at both ends. <b>0</b> is essentially their first game: you win that
 * fight almost every time. <b>10</b> is ranked-tier, a very high final kill/death ratio, and you
 * very likely lose. Everything between is calibrated against those, and the tests assert it.
 *
 * <p>Lifetime stats lead, because they are what predicts a fight before it starts and they are
 * known the moment someone joins. Gear counts heavily but only when it can actually be seen —
 * outside render distance it is unknown, not absent, and the factor drops out rather than scoring
 * zero. Bed state and how the current game is going are small adjustments on top, not co-equal
 * terms, so an excellent player with an untouched bed and no kills yet still reads as excellent.
 *
 * <p>No Minecraft references, so all of this is tested directly.
 */
public final class ThreatEngine {

    /** The FKDR that scores a full 10. Ten is already exceptional; twenty is nearly nobody. */
    private static final double FKDR_CEILING = 12.0;

    /** The Bedwars level that scores a full 10 on the star term. */
    private static final double STAR_CEILING = 400.0;

    /** FKDR is skill; star is mostly time played. Weighted accordingly within the stats term. */
    private static final double FKDR_SHARE = 0.8;

    /**
     * Final kills plus deaths needed before a ratio is taken at face value. Below it the score is
     * pulled toward {@link #NEW_PLAYER_SCORE}, because someone who is 5 and 0 has not proved
     * anything — they have played five fights.
     */
    private static final double CONFIDENCE_SAMPLE = 60.0;

    /** Where a record too thin to judge lands. Low, since zero means a first game. */
    private static final double NEW_PLAYER_SCORE = 0.5;

    /**
     * Where a confirmed cheat flag floors the score. They also sort above everyone regardless, so
     * this number is about reading as extreme rather than about winning the ordering.
     */
    private static final double CHEAT_FLOOR = 9.5;

    private ThreatEngine() {
    }

    public static ThreatEntry evaluate(ThreatInput input) {
        return evaluate(input, ThreatWeights.DEFAULT);
    }

    public static ThreatEntry evaluate(ThreatInput input, ThreatWeights weights) {
        BedwarsStats stats = input.getStats();
        boolean statsCounted = !stats.isUnknown();
        boolean gearCounted = input.isGearObserved();

        double statsScore = statsCounted ? scoreStats(stats) : 0.0;
        double gearScore = gearCounted ? scoreGear(input.getGear()) : 0.0;

        double statsWeight = statsCounted ? weights.getStats() : 0.0;
        double gearWeight = gearCounted ? weights.getGear() : 0.0;
        double totalWeight = statsWeight + gearWeight;

        double base;
        if (totalWeight <= 0.0) {
            // Nothing observable at all: a nicked player who has not loaded in. Say so with a low
            // number rather than inventing one.
            base = NEW_PLAYER_SCORE;
        } else {
            base = (statsScore * statsWeight + gearScore * gearWeight) / totalWeight;
        }

        double modifier = bedModifier(input) + formModifier(input);
        double score = clamp(base + modifier);

        if (input.isFlaggedForCheating()) {
            score = Math.max(score, CHEAT_FLOOR);
        }

        return new ThreatEntry(input, score, statsScore, gearScore, modifier, statsCounted);
    }

    /**
     * Blends final kill/death ratio with Bedwars level.
     *
     * <p>Both are log-scaled. The distance between 2 and 6 FKDR is a far bigger difference in
     * practice than the distance between 20 and 40, and a linear scale would compress almost every
     * real player into the bottom of the range.
     */
    private static double scoreStats(BedwarsStats stats) {
        double fkdrPart = 10.0 * Math.log1p(Math.max(0.0, stats.getFinalKillDeathRatio()))
                / Math.log1p(FKDR_CEILING);
        double starPart = 10.0 * Math.log1p(Math.max(0, stats.getStar())) / Math.log1p(STAR_CEILING);
        double raw = clamp(FKDR_SHARE * fkdrPart + (1.0 - FKDR_SHARE) * starPart);

        double sample = stats.getFinalKills() + stats.getFinalDeaths();
        double confidence = Math.min(1.0, sample / CONFIDENCE_SAMPLE);
        return NEW_PLAYER_SCORE + (raw - NEW_PLAYER_SCORE) * confidence;
    }

    /** Armour and weapon tier, each nudged by its enchantment level. */
    private static double scoreGear(Gear gear) {
        double armour = gear.getArmour().getPoints() + Math.min(1.0, gear.getProtection() * 0.25);
        double weapon = gear.getWeapon().getPoints() + Math.min(1.5, gear.getSharpness() * 0.75);
        return clamp(0.55 * armour + 0.45 * weapon);
    }

    /**
     * An intact bed means every kill you take off them has to be taken again, which is a longer
     * lived threat than a bedless player on the same stats.
     */
    private static double bedModifier(ThreatInput input) {
        return input.isBedIntact() ? 0.3 : -0.5;
    }

    /** How the current game is going for them, as a bounded adjustment. */
    private static double formModifier(ThreatInput input) {
        double kills = Math.min(1.2, input.getKillsThisGame() * 0.25);
        double deaths = Math.min(0.6, input.getDeathsThisGame() * 0.15);
        return kills - deaths;
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
