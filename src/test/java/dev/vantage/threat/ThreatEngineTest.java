package dev.vantage.threat;

import com.google.gson.JsonParser;
import dev.vantage.hypixel.BedwarsStats;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatEngineTest {

    /** Builds a record the same way the API delivers one, so the parser is exercised too. */
    private static BedwarsStats stats(int star, int finalKills, int finalDeaths,
                                      int wins, int losses, int kills, int deaths) {
        String text = "{\"player\":{\"achievements\":{\"bedwars_level\":" + star + "},"
                + "\"stats\":{\"Bedwars\":{"
                + "\"final_kills_bedwars\":" + finalKills
                + ",\"final_deaths_bedwars\":" + finalDeaths
                + ",\"wins_bedwars\":" + wins
                + ",\"losses_bedwars\":" + losses
                + ",\"kills_bedwars\":" + kills
                + ",\"deaths_bedwars\":" + deaths
                + "}}}}";
        return BedwarsStats.parse(new JsonParser().parse(text).getAsJsonObject());
    }

    // Six real shapes of Bedwars player, from someone's first night to a leaderboard name. The
    // whole feature is the claim that these six land in six visibly different places.

    private static final BedwarsStats FIRST_GAME = stats(2, 6, 15, 1, 10, 20, 40);
    private static final BedwarsStats CASUAL = stats(40, 300, 250, 200, 250, 3000, 2800);
    private static final BedwarsStats DECENT = stats(120, 1500, 600, 600, 450, 9000, 6000);
    private static final BedwarsStats GOOD = stats(200, 4000, 800, 1200, 600, 20000, 8000);
    private static final BedwarsStats VERY_GOOD = stats(350, 12000, 1500, 3000, 1000, 50000, 15000);
    private static final BedwarsStats RANKED = stats(500, 40000, 2667, 8000, 1600, 120000, 24000);

    private static final Gear FULL_DIAMOND = new Gear(Gear.Armour.DIAMOND, 2, Gear.Weapon.DIAMOND, 2);
    private static final Gear STARTING = new Gear(Gear.Armour.LEATHER, 0, Gear.Weapon.WOOD, 0);

    /** The usual case in Bedwars: a player in the lobby who is not rendered, so gear is unknown. */
    private static double scoreOf(BedwarsStats stats) {
        return ThreatEngine.evaluate(ThreatInput.builder("P").stats(stats).gearUnknown().build())
                .getScore();
    }

    private static void inBand(String profile, double score, double low, double high) {
        assertTrue(score >= low && score <= high,
                profile + " scored " + String.format(Locale.ROOT, "%.2f", score)
                        + ", expected " + low + " to " + high);
    }

    // -- calibration ------------------------------------------------------------------------
    // 0 means essentially their first game; 10 means a leaderboard name and you very likely lose.
    // These bands are the contract the whole feature rests on.

    @Test
    void aFirstGamePlayerScoresNearZero() {
        inBand("first game", scoreOf(FIRST_GAME), 0.0, 2.5);
    }

    @Test
    void aCasualPlayerScoresLow() {
        inBand("casual, 1.2 fkdr", scoreOf(CASUAL), 3.0, 4.5);
    }

    @Test
    void aDecentPlayerScoresMiddling() {
        inBand("decent, 2.5 fkdr", scoreOf(DECENT), 4.8, 6.2);
    }

    @Test
    void aGoodPlayerScoresHigh() {
        inBand("good, 5 fkdr", scoreOf(GOOD), 6.5, 8.0);
    }

    @Test
    void aVeryGoodPlayerScoresVeryHigh() {
        inBand("very good, 8 fkdr", scoreOf(VERY_GOOD), 8.2, 9.4);
    }

    @Test
    void aRankedTierPlayerScoresNearTen() {
        inBand("ranked tier, 15 fkdr", scoreOf(RANKED), 9.4, 10.0);
    }

    @Test
    void theScaleIsMonotonic() {
        double[] ladder = {
                scoreOf(FIRST_GAME), scoreOf(CASUAL), scoreOf(DECENT),
                scoreOf(GOOD), scoreOf(VERY_GOOD), scoreOf(RANKED),
        };
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i] > ladder[i - 1],
                    "rung " + i + " (" + ladder[i] + ") should beat " + ladder[i - 1]);
        }
    }

    @Test
    void theSixProfilesSpreadAcrossTheWholeScale() {
        // The complaint that started this: everyone scored roughly the same, so the number told you
        // nothing. Adjacent rungs have to be far enough apart to read as different players.
        double[] ladder = {
                scoreOf(FIRST_GAME), scoreOf(CASUAL), scoreOf(DECENT),
                scoreOf(GOOD), scoreOf(VERY_GOOD), scoreOf(RANKED),
        };
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i] - ladder[i - 1] > 1.0,
                    "rungs " + (i - 1) + " and " + i + " are only "
                            + String.format(Locale.ROOT, "%.2f", ladder[i] - ladder[i - 1]) + " apart");
        }
        assertTrue(ladder[ladder.length - 1] - ladder[0] > 7.5, "the scale should use its range");
    }

    // -- each ratio pulls its own weight ------------------------------------------------------

    @Test
    void eachRatioMovesTheScoreOnItsOwn() {
        // Holding the other two fixed, raising any one of the three has to raise the score.
        // Otherwise a term is decorative and the formula is lying about what it measures.
        double baseline = scoreOf(stats(100, 1000, 500, 400, 400, 5000, 5000));
        assertTrue(scoreOf(stats(100, 2000, 500, 400, 400, 5000, 5000)) > baseline, "fkdr");
        assertTrue(scoreOf(stats(100, 1000, 500, 800, 400, 5000, 5000)) > baseline, "win/loss");
        assertTrue(scoreOf(stats(100, 1000, 500, 400, 400, 9000, 5000)) > baseline, "kdr");
    }

    @Test
    void fkdrCountsForMoreThanTheOtherTwo() {
        double baseline = scoreOf(stats(100, 1000, 500, 400, 400, 5000, 5000));
        // Doubling each ratio in turn: final kills have to be worth the most.
        double byFkdr = scoreOf(stats(100, 2000, 500, 400, 400, 5000, 5000)) - baseline;
        double byWlr = scoreOf(stats(100, 1000, 500, 800, 400, 5000, 5000)) - baseline;
        double byKdr = scoreOf(stats(100, 1000, 500, 400, 400, 10000, 5000)) - baseline;
        assertTrue(byFkdr > byWlr && byFkdr > byKdr,
                "fkdr " + byFkdr + ", wlr " + byWlr + ", kdr " + byKdr);
    }

    @Test
    void starIsABonusRatherThanAFourthRatio() {
        // Level is mostly time played. A grinder with a mediocre record must not out-rank a good
        // player, however many stars they have.
        double grinder = scoreOf(stats(500, 2000, 2000, 500, 700, 12000, 14000));
        double good = scoreOf(GOOD);
        assertTrue(good > grinder, "star-500 grinder scored " + grinder + " against " + good);
    }

    @Test
    void starNeverAddsMoreThanAPoint() {
        BedwarsStats noStar = stats(0, 1000, 500, 400, 400, 5000, 5000);
        BedwarsStats absurdStar = stats(5000, 1000, 500, 400, 400, 5000, 5000);
        assertTrue(scoreOf(absurdStar) - scoreOf(noStar) <= 1.0 + 1e-9);
    }

    // -- gear: the rating-swing bug -----------------------------------------------------------

    @Test
    void gearNeverMovesAScoreByMoreThanAboutAPoint() {
        // The regression that started this. Gear used to carry about a third of the score, so
        // walking out of render distance moved a rating by a point and a half.
        for (BedwarsStats profile : Arrays.asList(FIRST_GAME, CASUAL, DECENT, GOOD, VERY_GOOD)) {
            double unseen = scoreOf(profile);
            double diamond = ThreatEngine.evaluate(
                    ThreatInput.builder("P").stats(profile).gear(FULL_DIAMOND).build()).getScore();
            double leather = ThreatEngine.evaluate(
                    ThreatInput.builder("P").stats(profile).gear(STARTING).build()).getScore();
            assertTrue(Math.abs(diamond - unseen) <= 1.25, "diamond swing " + (diamond - unseen));
            assertTrue(Math.abs(leather - unseen) <= 1.25, "leather swing " + (leather - unseen));
        }
    }

    @Test
    void gearStillCountsInTheRightDirection() {
        double unseen = scoreOf(DECENT);
        double diamond = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(DECENT).gear(FULL_DIAMOND).build()).getScore();
        double leather = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(DECENT).gear(STARTING).build()).getScore();
        assertTrue(diamond > unseen, "seeing full diamond is real information");
        assertTrue(leather < unseen, "so is seeing leather and a wooden sword");
    }

    @Test
    void gearCannotReorderPlayersWhoseStatsClearlyDiffer() {
        // A casual player in full diamond must still rank below a good player in nothing, or the
        // list is ranking shopping trips rather than players.
        double casualGeared = ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(CASUAL).gear(FULL_DIAMOND).build()).getScore();
        double goodBare = ThreatEngine.evaluate(
                ThreatInput.builder("Q").stats(GOOD).gear(STARTING).build()).getScore();
        assertTrue(goodBare > casualGeared, casualGeared + " vs " + goodBare);
    }

    @Test
    void anOldGearReadingFadesInsteadOfSnapping() {
        // Walking behind a wall must not change a rating. The last sighting is held, then fades,
        // so the number settles rather than stepping the moment someone leaves render distance.
        ThreatInput.Builder base = ThreatInput.builder("P").stats(DECENT);
        double fresh = ThreatEngine.evaluate(base.gear(FULL_DIAMOND, 0L).build()).getScore();
        double held = ThreatEngine.evaluate(base.gear(FULL_DIAMOND, 10_000L).build()).getScore();
        double fading = ThreatEngine.evaluate(base.gear(FULL_DIAMOND, 22_500L).build()).getScore();
        double gone = ThreatEngine.evaluate(base.gear(FULL_DIAMOND, 40_000L).build()).getScore();
        double never = scoreOf(DECENT);

        assertEquals(fresh, held, 1e-9, "a recent sighting is still worth full weight");
        assertTrue(fading < fresh && fading > gone, "it should fade, not step");
        assertEquals(never, gone, 1e-9, "an old enough sighting is the same as never having seen them");
    }

    // -- what no longer counts ----------------------------------------------------------------

    @Test
    void bedStateDoesNotMoveTheScore() {
        ThreatInput.Builder base = ThreatInput.builder("P").stats(DECENT).gearUnknown();
        assertEquals(ThreatEngine.evaluate(base.bedIntact(true).build()).getScore(),
                ThreatEngine.evaluate(base.bedIntact(false).build()).getScore(), 1e-9);
    }

    @Test
    void thisGamesKillsAndDeathsDoNotMoveTheScore() {
        // They are shown, not scored. Letting them in is what made a rating lurch every few
        // seconds off the kill feed without ever becoming more accurate.
        ThreatInput.Builder base = ThreatInput.builder("P").stats(DECENT).gearUnknown();
        double quiet = ThreatEngine.evaluate(base.killsThisGame(0).deathsThisGame(0).build()).getScore();
        double busy = ThreatEngine.evaluate(base.killsThisGame(9).deathsThisGame(4).build()).getScore();
        assertEquals(quiet, busy, 1e-9);
    }

    // -- thin records -------------------------------------------------------------------------

    @Test
    void aThinRecordIsPulledTowardTheMiddleAndMarked() {
        // Five final kills and no deaths is not a 10 fkdr player, it is five fights.
        ThreatEntry thin = ThreatEngine.evaluate(ThreatInput.builder("P")
                .stats(stats(3, 5, 0, 2, 1, 40, 20)).gearUnknown().build());
        assertTrue(thin.isLowSample(), "a five fight record must be marked as unproven");
        assertTrue(thin.getScore() < 4.0, "scored " + thin.getScore());
    }

    @Test
    void aFullRecordIsNotMarked() {
        assertFalse(ThreatEngine.evaluate(
                ThreatInput.builder("P").stats(GOOD).gearUnknown().build()).isLowSample());
    }

    // -- nicked -------------------------------------------------------------------------------

    @Test
    void aNickedPlayerIsTreatedAsALikelySmurf() {
        // A nick hides a record. On Hypixel the players who bother are far more often good ones
        // avoiding attention than beginners, so the absence itself is the signal.
        ThreatEntry nicked = ThreatEngine.evaluate(ThreatInput.builder("Nick")
                .stats(BedwarsStats.UNKNOWN).nicked(true).gearUnknown().build());
        assertFalse(nicked.isStatsCounted());
        assertEquals(ThreatEngine.NICKED_SCORE, nicked.getScore(), 1e-9);
    }

    @Test
    void aNickedPlayerInGoodGearRanksHigherStill() {
        double bare = ThreatEngine.evaluate(ThreatInput.builder("Nick")
                .stats(BedwarsStats.UNKNOWN).nicked(true).gearUnknown().build()).getScore();
        double geared = ThreatEngine.evaluate(ThreatInput.builder("Nick")
                .stats(BedwarsStats.UNKNOWN).nicked(true).gear(FULL_DIAMOND).build()).getScore();
        assertTrue(geared > bare);
    }

    @Test
    void aLookupThatHasNotLandedYetReadsAsUnprovenRatherThanNicked() {
        // Stats not fetched yet is not the same as stats hidden, and guessing "smurf" for every
        // player in the first second of a game would make the list useless.
        ThreatEntry pending = ThreatEngine.evaluate(ThreatInput.builder("P")
                .stats(BedwarsStats.UNKNOWN).nicked(false).gearUnknown().build());
        assertTrue(pending.getScore() < ThreatEngine.NICKED_SCORE, "scored " + pending.getScore());
        assertTrue(pending.isLowSample());
    }

    // -- cheating ---------------------------------------------------------------------------

    @Test
    void aFlaggedCheaterIsTheTopThreatWhateverTheirStats() {
        ThreatEntry flagged = ThreatEngine.evaluate(ThreatInput.builder("Cheat")
                .stats(FIRST_GAME).gearUnknown().flaggedForCheating(true).build());
        assertTrue(flagged.getScore() >= 9.0, "scored " + flagged.getScore());
    }

    @Test
    void flaggedPlayersSortAboveGenuinelyGoodOnes() {
        List<ThreatInput> lobby = Arrays.asList(
                ThreatInput.builder("Legit").stats(VERY_GOOD).gear(FULL_DIAMOND).build(),
                ThreatInput.builder("Cheat").stats(FIRST_GAME).gearUnknown()
                        .flaggedForCheating(true).build());
        assertEquals("Cheat", ThreatEngine.rank(lobby).get(0).getName());
    }

    @Test
    void aFlagNeverLowersAnAlreadyHigherScore() {
        ThreatInput.Builder base = ThreatInput.builder("P").stats(RANKED).gear(FULL_DIAMOND);
        double clean = ThreatEngine.evaluate(base.flaggedForCheating(false).build()).getScore();
        double flagged = ThreatEngine.evaluate(base.flaggedForCheating(true).build()).getScore();
        assertTrue(flagged >= clean);
    }

    // -- ranking ------------------------------------------------------------------------------

    @Test
    void youAreRankedAlongsideEveryoneElse() {
        // The point of a scale: your own number sits in the same list, so the players above you
        // are the ones you lose to and the players below are the ones you do not.
        List<ThreatInput> lobby = Arrays.asList(
                ThreatInput.builder("Better").stats(RANKED).gearUnknown().build(),
                ThreatInput.builder("Me").stats(DECENT).gearUnknown().self(true).build(),
                ThreatInput.builder("Worse").stats(FIRST_GAME).gearUnknown().build());

        List<ThreatEntry> ranked = ThreatEngine.rank(lobby);
        assertEquals(3, ranked.size());
        assertEquals("Better", ranked.get(0).getName());
        assertEquals("Me", ranked.get(1).getName());
        assertTrue(ranked.get(1).isSelf());
        assertEquals("Worse", ranked.get(2).getName());
    }

    @Test
    void tiesBreakByNameSoTheListDoesNotShuffle() {
        List<ThreatInput> lobby = Arrays.asList(
                ThreatInput.builder("Zeta").stats(DECENT).gearUnknown().build(),
                ThreatInput.builder("Alpha").stats(DECENT).gearUnknown().build());
        assertEquals("Alpha", ThreatEngine.rank(lobby).get(0).getName());
    }

    // -- edges ------------------------------------------------------------------------------

    @Test
    void scoresAlwaysLandInRange() {
        double top = ThreatEngine.evaluate(ThreatInput.builder("Max")
                .stats(stats(5000, 900000, 1, 90000, 1, 900000, 1)).gear(FULL_DIAMOND).build())
                .getScore();
        assertTrue(top <= 10.0 && top >= 0.0, "scored " + top);

        double bottom = ThreatEngine.evaluate(ThreatInput.builder("Min")
                .stats(stats(0, 0, 0, 0, 0, 0, 0)).gear(Gear.EMPTY).build()).getScore();
        assertTrue(bottom >= 0.0 && bottom <= 10.0, "scored " + bottom);
    }

    @Test
    void anAccountThatHasNeverTouchedBedwarsDoesNotCrashOrRankHigh() {
        BedwarsStats fresh = BedwarsStats.parse(new JsonParser()
                .parse("{\"player\":{\"achievements\":{\"bedwars_level\":1}}}").getAsJsonObject());
        double score = scoreOf(fresh);
        assertTrue(score >= 0.0 && score < 3.0, "scored " + score);
    }

    @Test
    void gearShorthandReadsAtAGlance() {
        assertEquals("D/D", FULL_DIAMOND.shorthand());
        assertEquals("-/-", Gear.EMPTY.shorthand());
        assertEquals("L/W", STARTING.shorthand());
    }
}
