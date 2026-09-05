package dev.vantage.threat;

import com.google.gson.JsonParser;
import dev.vantage.hypixel.BedwarsStats;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatEngineTest {

    private static BedwarsStats stats(int star, int finalKills, int finalDeaths) {
        String text = "{\"player\":{\"achievements\":{\"bedwars_level\":" + star + "},"
                + "\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":" + finalKills
                + ",\"final_deaths_bedwars\":" + finalDeaths + "}}}}";
        return BedwarsStats.parse(new JsonParser().parse(text).getAsJsonObject());
    }

    private static final Gear FULL_DIAMOND = new Gear(Gear.Armour.DIAMOND, 2, Gear.Weapon.DIAMOND, 2);
    private static final Gear STARTING = new Gear(Gear.Armour.LEATHER, 0, Gear.Weapon.WOOD, 0);

    /** The usual case in Bedwars: a player in the lobby who is not rendered, so gear is unknown. */
    private static double scoreOf(BedwarsStats stats) {
        return ThreatEngine.evaluate(ThreatInput.builder("P").stats(stats).gearUnknown().build()).getScore();
    }

    private static void inBand(String profile, double score, double low, double high) {
        assertTrue(score >= low && score <= high,
                profile + " scored " + String.format(java.util.Locale.ROOT, "%.2f", score)
                        + ", expected " + low + " to " + high);
    }

    // -- calibration ------------------------------------------------------------------------
    // 0 means essentially their first game; 10 means ranked tier and you very likely lose.
    // These bands are the contract the whole feature rests on.

    @Test
    void aFirstGamePlayerScoresNearZero() {
        inBand("first game", scoreOf(stats(2, 6, 15)), 0.0, 1.5);
    }

    @Test
    void aCasualPlayerScoresLow() {
        inBand("casual, 1.2 fkdr", scoreOf(stats(40, 300, 250)), 2.5, 4.5);
    }

    @Test
    void aDecentPlayerScoresMiddling() {
        inBand("decent, 2.5 fkdr", scoreOf(stats(120, 1500, 600)), 4.5, 6.5);
    }

    @Test
    void aGoodPlayerScoresHigh() {
        inBand("good, 5 fkdr", scoreOf(stats(200, 4000, 800)), 6.5, 8.0);
    }

    @Test
    void aVeryGoodPlayerScoresVeryHigh() {
        inBand("very good, 8 fkdr", scoreOf(stats(350, 12000, 1500)), 8.0, 9.3);
    }

    @Test
    void aRankedTierPlayerScoresNearTen() {
        inBand("ranked tier, 15 fkdr", scoreOf(stats(500, 40000, 2667)), 9.3, 10.0);
    }

    @Test
    void theScaleIsMonotonic() {
        double[] ladder = {
                scoreOf(stats(2, 6, 15)),
                scoreOf(stats(40, 300, 250)),
                scoreOf(stats(120, 1500, 600)),
                scoreOf(stats(200, 4000, 800)),
                scoreOf(stats(350, 12000, 1500)),
                scoreOf(stats(500, 40000, 2667)),
        };
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i] > ladder[i - 1],
                    "rung " + i + " (" + ladder[i] + ") should beat " + ladder[i - 1]);
        }
    }

    // -- the bug that started this ------------------------------------------------------------

    @Test
    void unseenGearDoesNotDragAGoodPlayerDown() {
        // The regression: gear counted at full weight even when the player was not rendered, so a
        // strong player out of render distance scored around 3.5 instead of around 7.5.
        BedwarsStats good = stats(200, 4000, 800);
        double unseen = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(good).gearUnknown().build()).getScore();
        assertTrue(unseen > 6.5, "a good player with unseen gear scored " + unseen);
    }

    @Test
    void unknownGearScoresAsIfOnlyStatsWereKnown() {
        BedwarsStats good = stats(200, 4000, 800);
        ThreatEntry unknown = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(good).gearUnknown().build());
        // Dropping the factor must renormalise, not leave a hole: the base should be the stats
        // score itself, plus only the bed modifier.
        assertEquals(unknown.getStatsScore() + unknown.getModifier(), unknown.getScore(), 1e-9);
    }

    @Test
    void observedStartingGearLegitimatelyLowersTheScore() {
        // Seeing someone in leather and wood is real information and should count against them.
        BedwarsStats good = stats(200, 4000, 800);
        double unseen = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(good).gearUnknown().build()).getScore();
        double bare = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(good).gear(STARTING).build()).getScore();
        assertTrue(bare < unseen, "observed weak gear should lower the score");
    }

    @Test
    void observedGoodGearRaisesIt() {
        BedwarsStats average = stats(120, 1500, 600);
        double unseen = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(average).gearUnknown().build()).getScore();
        double geared = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(average).gear(FULL_DIAMOND).build()).getScore();
        assertTrue(geared > unseen);
    }

    // -- modifiers --------------------------------------------------------------------------

    @Test
    void bedStateMovesTheScoreByASmallFixedAmount() {
        ThreatInput.Builder base = ThreatInput.builder("P").stats(stats(120, 1500, 600)).gearUnknown();
        double withBed = ThreatEngine.evaluate(base.bedIntact(true).build()).getScore();
        double without = ThreatEngine.evaluate(base.bedIntact(false).build()).getScore();
        assertEquals(0.8, withBed - without, 1e-9, "bed should be an adjustment, not a dominant term");
    }

    @Test
    void killsThisGameRaiseTheScoreButAreCapped() {
        ThreatInput.Builder base = ThreatInput.builder("P").stats(stats(120, 1500, 600)).gearUnknown();
        double none = ThreatEngine.evaluate(base.killsThisGame(0).build()).getScore();
        double two = ThreatEngine.evaluate(base.killsThisGame(2).build()).getScore();
        double many = ThreatEngine.evaluate(base.killsThisGame(20).build()).getScore();

        assertEquals(0.5, two - none, 1e-9);
        assertEquals(1.2, many - none, 1e-9, "a long streak must not run away with the score");
    }

    @Test
    void deathsThisGameLowerItButAreCapped() {
        ThreatInput.Builder base = ThreatInput.builder("P").stats(stats(200, 4000, 800)).gearUnknown();
        double none = ThreatEngine.evaluate(base.deathsThisGame(0).build()).getScore();
        double many = ThreatEngine.evaluate(base.deathsThisGame(20).build()).getScore();
        assertEquals(0.6, none - many, 1e-9);
    }

    // -- cheating ---------------------------------------------------------------------------

    @Test
    void aFlaggedCheaterIsTheTopThreatWhateverTheirStats() {
        ThreatEntry flagged = ThreatEngine.evaluate(ThreatInput.builder("Cheat")
                .stats(stats(2, 6, 15)).gearUnknown().flaggedForCheating(true).build());
        assertTrue(flagged.getScore() >= 9.0, "scored " + flagged.getScore());
    }

    @Test
    void flaggedPlayersSortAboveGenuinelyGoodOnes() {
        List<ThreatInput> lobby = Arrays.asList(
                ThreatInput.builder("Legit").stats(stats(350, 12000, 1500)).gear(FULL_DIAMOND).build(),
                ThreatInput.builder("Cheat").stats(stats(5, 10, 20)).gearUnknown()
                        .flaggedForCheating(true).build());
        assertEquals("Cheat", ThreatEngine.rank(lobby, ThreatWeights.DEFAULT).get(0).getName());
    }

    @Test
    void aFlagNeverLowersAnAlreadyHigherScore() {
        ThreatInput.Builder base = ThreatInput.builder("P")
                .stats(stats(500, 40000, 2667)).gear(FULL_DIAMOND).killsThisGame(5);
        double clean = ThreatEngine.evaluate(base.flaggedForCheating(false).build()).getScore();
        double flagged = ThreatEngine.evaluate(base.flaggedForCheating(true).build()).getScore();
        assertTrue(flagged >= clean);
    }

    // -- edges ------------------------------------------------------------------------------

    @Test
    void aNickedPlayerWithGearIsScoredOnThatGear() {
        ThreatEntry nicked = ThreatEngine.evaluate(ThreatInput.builder("Nick")
                .stats(BedwarsStats.UNKNOWN).nicked(true).gear(FULL_DIAMOND).build());
        assertFalse(nicked.isStatsCounted());
        assertTrue(nicked.getScore() > 6.0, "full diamond is a real threat; scored " + nicked.getScore());
    }

    @Test
    void aNickedPlayerWithNothingObservableReadsLowRatherThanInvented() {
        ThreatEntry unknown = ThreatEngine.evaluate(ThreatInput.builder("Nick")
                .stats(BedwarsStats.UNKNOWN).nicked(true).gearUnknown().build());
        assertTrue(unknown.getScore() < 1.5, "scored " + unknown.getScore());
    }

    @Test
    void scoresAlwaysLandInRange() {
        double top = ThreatEngine.evaluate(ThreatInput.builder("Max")
                .stats(stats(5000, 900000, 1)).gear(FULL_DIAMOND).killsThisGame(99).build()).getScore();
        assertTrue(top <= 10.0 && top >= 0.0);

        double bottom = ThreatEngine.evaluate(ThreatInput.builder("Min")
                .stats(stats(0, 0, 0)).gear(Gear.EMPTY).bedIntact(false).deathsThisGame(9).build()).getScore();
        assertTrue(bottom >= 0.0 && bottom <= 10.0);
    }

    @Test
    void rankingExcludesYouAndBreaksTiesByName() {
        List<ThreatInput> lobby = Arrays.asList(
                ThreatInput.builder("Zeta").stats(stats(120, 1500, 600)).gearUnknown().build(),
                ThreatInput.builder("Alpha").stats(stats(120, 1500, 600)).gearUnknown().build(),
                ThreatInput.builder("Me").stats(stats(500, 40000, 2667)).gearUnknown().self(true).build());

        List<ThreatEntry> ranked = ThreatEngine.rank(lobby, ThreatWeights.DEFAULT);
        assertEquals(2, ranked.size(), "your own player should not be listed");
        assertEquals("Alpha", ranked.get(0).getName(), "ties must be stable between frames");
    }

    @Test
    void gearShorthandReadsAtAGlance() {
        assertEquals("D/D", FULL_DIAMOND.shorthand());
        assertEquals("-/-", Gear.EMPTY.shorthand());
        assertEquals("L/W", STARTING.shorthand());
    }
}
