package dev.vantage.threat;

import dev.vantage.hypixel.BedwarsStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns a player's public record into a single 0-10 rating.
 *
 * <p>The whole score is their lifetime stats plus what they are carrying, and nothing else. An
 * earlier version blended in bed state and how the current game was going, which moved every
 * player's number every few seconds without ever making it more accurate; those are gone.
 *
 * <p>Three ratios carry the score. <b>FKDR</b> leads, because it is the closest thing Bedwars has
 * to a direct measure of who wins a fight. <b>Win/loss</b> comes next: it is hard to farm and it
 * says whether they actually close games out. <b>KDR</b> counts least, since it mixes in void
 * deaths and non-final kills and is the noisiest of the three. Star sits on top as a small bonus
 * rather than a fourth ratio, because level is mostly time played.
 *
 * <p>Gear is a bounded adjustment, never a weight. Weighting it heavily is what made everyone score
 * alike: by mid-game the whole lobby owns iron or diamond, so gear converges and drowns out the
 * skill signal. Worse, it dropped out entirely the moment a player left render distance, which
 * moved their rating by a point and a half for walking behind a wall. Here it can shift a score by
 * about one point, and the last reading is held for a while after they go out of sight so it fades
 * rather than snaps.
 *
 * <p>No Minecraft references, so all of this is tested directly.
 */
public final class ThreatEngine {

    // -- the ceilings each ratio is measured against ------------------------------------------
    // Each is the value that scores a full 10 on its own term. They are set where the population
    // actually thins out, not at a round number: an FKDR of 15 or a 5.0 win/loss is a leaderboard
    // player, and pitching them lower would bunch every ordinary player against the top.

    public static final double FKDR_CEILING = 15.0;
    public static final double WLR_CEILING = 5.0;
    public static final double KDR_CEILING = 6.0;
    public static final double STAR_CEILING = 500.0;

    private static final double FKDR_SHARE = 0.55;
    private static final double WLR_SHARE = 0.25;
    private static final double KDR_SHARE = 0.20;

    /** The most a maxed-out star can add. Level is time played, so it nudges rather than decides. */
    private static final double STAR_BONUS = 1.0;

    /** Gear points sit on the same 0-10 scale, so this puts the swing at roughly plus or minus one. */
    private static final double GEAR_INFLUENCE = 0.24;

    /** The gear score an average mid-game loadout earns, and so the point where gear stops mattering. */
    private static final double GEAR_NEUTRAL = 5.0;

    /** How long a gear reading stays at full strength after the player leaves render distance. */
    private static final long GEAR_HOLD_MILLIS = 15_000L;

    /** How long it then takes to fade to nothing. */
    private static final long GEAR_FADE_MILLIS = 15_000L;

    /**
     * Final kills plus deaths needed before the ratios are taken at face value. Below it the score
     * is pulled toward {@link #UNPROVEN_SCORE}, because someone who is 5 and 0 has not proved
     * anything — they have played five fights.
     */
    private static final double CONFIDENCE_SAMPLE = 30.0;

    /** Where a record too thin to judge lands: unremarkable, and marked as such in the list. */
    private static final double UNPROVEN_SCORE = 2.0;

    /**
     * What a nicked player scores.
     *
     * <p>High on purpose. A nick hides a record, and on Hypixel the players who bother are far more
     * often good ones avoiding attention than beginners. Guessing low here is the mistake that gets
     * you killed by the one name you ignored.
     */
    public static final double NICKED_SCORE = 7.0;

    /**
     * Where a confirmed cheat flag floors the score. They also sort above everyone regardless, so
     * this number is about reading as extreme rather than about winning the ordering.
     */
    private static final double CHEAT_FLOOR = 9.5;

    private ThreatEngine() {
    }

    public static ThreatEntry evaluate(ThreatInput input) {
        BedwarsStats stats = input.getStats();
        boolean statsCounted = !stats.isUnknown();

        double skill;
        boolean lowSample;
        if (input.isNicked()) {
            // Nothing to measure, and the absence is itself the signal.
            skill = NICKED_SCORE;
            lowSample = false;
        } else if (statsCounted) {
            skill = scoreStats(stats);
            lowSample = sampleConfidence(stats) < 1.0;
        } else {
            // A lookup that has not come back yet. Say "unproven" rather than inventing a number.
            skill = UNPROVEN_SCORE;
            lowSample = true;
        }

        double starBonus = statsCounted ? starBonus(stats) : 0.0;
        double gearModifier = gearModifier(input);

        double score = clamp(skill + starBonus + gearModifier);
        if (input.isFlaggedForCheating()) {
            score = Math.max(score, CHEAT_FLOOR);
        }

        return new ThreatEntry(input, score, skill, starBonus, gearModifier, statsCounted, lowSample);
    }

    /**
     * Blends the three lifetime ratios.
     *
     * <p>All three go through the same log curve, which is what makes them comparable and what
     * keeps the scale usable. The gap between 2 and 6 FKDR is a far bigger difference in practice
     * than the gap between 20 and 40, and on a linear scale almost every real player would be
     * squashed into the bottom of the range — which was the original complaint.
     */
    private static double scoreStats(BedwarsStats stats) {
        double raw = FKDR_SHARE * curve(stats.getFinalKillDeathRatio(), FKDR_CEILING)
                + WLR_SHARE * curve(stats.getWinLossRatio(), WLR_CEILING)
                + KDR_SHARE * curve(stats.getKillDeathRatio(), KDR_CEILING);

        double confidence = sampleConfidence(stats);
        return UNPROVEN_SCORE + (clamp(raw) - UNPROVEN_SCORE) * confidence;
    }

    private static double sampleConfidence(BedwarsStats stats) {
        double sample = stats.getFinalKills() + stats.getFinalDeaths();
        return Math.min(1.0, sample / CONFIDENCE_SAMPLE);
    }

    /** Star as a bounded bonus on top, worth at most {@link #STAR_BONUS} however high the level. */
    private static double starBonus(BedwarsStats stats) {
        return Math.min(STAR_BONUS, STAR_BONUS * curve(stats.getStar(), STAR_CEILING) / 10.0);
    }

    /**
     * How much better or worse than average their loadout is.
     *
     * <p>Zero when their gear has never been seen, and fading toward zero once the last sighting
     * gets old. Unknown gear is not the same as no gear, and treating it as none is what made a
     * player's rating drop the moment they walked out of render distance.
     */
    private static double gearModifier(ThreatInput input) {
        if (!input.isGearObserved()) {
            return 0.0;
        }
        double freshness = gearFreshness(input.getGearAgeMillis());
        if (freshness <= 0.0) {
            return 0.0;
        }
        return (scoreGear(input.getGear()) - GEAR_NEUTRAL) * GEAR_INFLUENCE * freshness;
    }

    static double gearFreshness(long ageMillis) {
        if (ageMillis <= GEAR_HOLD_MILLIS) {
            return 1.0;
        }
        double faded = (ageMillis - GEAR_HOLD_MILLIS) / (double) GEAR_FADE_MILLIS;
        return Math.max(0.0, 1.0 - faded);
    }

    /** Armour and weapon tier, each nudged by its enchantment level, on the same 0-10 scale. */
    static double scoreGear(Gear gear) {
        double armour = gear.getArmour().getPoints() + Math.min(1.0, gear.getProtection() * 0.25);
        double weapon = gear.getWeapon().getPoints() + Math.min(1.5, gear.getSharpness() * 0.75);
        return clamp(0.55 * armour + 0.45 * weapon);
    }

    /**
     * The shared log curve: 0 maps to 0, {@code ceiling} maps to 10, and everything between is
     * compressed the further up it sits.
     */
    static double curve(double value, double ceiling) {
        if (ceiling <= 0.0) {
            return 0.0;
        }
        double safe = Math.max(0.0, value);
        return 10.0 * Math.log1p(safe) / Math.log1p(ceiling);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return value < 0.0 ? 0.0 : (value > 10.0 ? 10.0 : value);
    }

    /** Scores a whole lobby, most dangerous first. You are included, so you can see your own rank. */
    public static List<ThreatEntry> rank(List<ThreatInput> inputs) {
        List<ThreatEntry> entries = new ArrayList<ThreatEntry>(inputs.size());
        for (ThreatInput input : inputs) {
            entries.add(evaluate(input));
        }
        Collections.sort(entries);
        return entries;
    }
}
