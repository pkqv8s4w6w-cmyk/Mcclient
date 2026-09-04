package dev.vantage.threat;

import com.google.gson.JsonObject;
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
        JsonObject json = new JsonParser().parse(text).getAsJsonObject();
        return BedwarsStats.parse(json);
    }

    private static final Gear FULL_DIAMOND = new Gear(Gear.Armour.DIAMOND, 2, Gear.Weapon.DIAMOND, 2);
    private static final Gear STARTING = new Gear(Gear.Armour.LEATHER, 0, Gear.Weapon.WOOD, 0);

    @Test
    void aBetterRecordScoresHigher() {
        ThreatEntry strong = ThreatEngine.evaluate(ThreatInput.builder("Strong")
                .stats(stats(400, 6000, 500)).gear(STARTING).build());
        ThreatEntry weak = ThreatEngine.evaluate(ThreatInput.builder("Weak")
                .stats(stats(20, 200, 400)).gear(STARTING).build());
        assertTrue(strong.getScore() > weak.getScore());
    }

    @Test
    void betterGearScoresHigher() {
        ThreatInput.Builder base = ThreatInput.builder("P").stats(stats(100, 1000, 500));
        double geared = ThreatEngine.evaluate(base.gear(FULL_DIAMOND).build()).getScore();
        double bare = ThreatEngine.evaluate(base.gear(Gear.EMPTY).build()).getScore();
        assertTrue(geared > bare);
    }

    @Test
    void anIntactBedIsMoreThreateningThanNone() {
        ThreatInput.Builder base = ThreatInput.builder("P").stats(stats(100, 1000, 500)).gear(STARTING);
        double withBed = ThreatEngine.evaluate(base.bedIntact(true).build()).getScore();
        double withoutBed = ThreatEngine.evaluate(base.bedIntact(false).build()).getScore();
        assertTrue(withBed > withoutBed);
    }

    @Test
    void aThinRecordIsPulledTowardNeutral() {
        // 5 final kills and no deaths is an FKDR of 5, but it is five kills. It must not rate
        // near a genuine 5.0 FKDR player with thousands of games.
        double thin = ThreatEngine.evaluate(ThreatInput.builder("New")
                .stats(stats(3, 5, 0)).gear(STARTING).build()).getStatsScore();
        double established = ThreatEngine.evaluate(ThreatInput.builder("Old")
                .stats(stats(200, 5000, 1000)).gear(STARTING).build()).getStatsScore();
        assertTrue(thin < established, "thin record " + thin + " should rate below " + established);
    }

    @Test
    void aNickedPlayerStillScoresOnGearInsteadOfCollapsingToZero() {
        ThreatEntry nicked = ThreatEngine.evaluate(ThreatInput.builder("Nick")
                .stats(BedwarsStats.UNKNOWN).nicked(true).gear(FULL_DIAMOND).killsThisGame(3).build());
        assertFalse(nicked.isStatsCounted());
        assertTrue(nicked.getScore() > 5.0,
                "a nicked player in full diamond is a real threat, scored " + nicked.getScore());
    }

    @Test
    void unknownStatsDoNotDragTheScoreDownByCountingAsZero() {
        ThreatInput.Builder base = ThreatInput.builder("P").gear(FULL_DIAMOND).killsThisGame(2);
        double unknownStats = ThreatEngine.evaluate(base.stats(BedwarsStats.UNKNOWN).build()).getScore();
        // Dropping the factor should leave roughly the gear and momentum blend, well above what
        // treating unknown as a zero stats score would give.
        assertTrue(unknownStats > 6.0, "scored " + unknownStats);
    }

    @Test
    void scoresAlwaysLandInRange() {
        ThreatEntry extreme = ThreatEngine.evaluate(ThreatInput.builder("Max")
                .stats(stats(5000, 900000, 1)).gear(FULL_DIAMOND).killsThisGame(99).build());
        assertTrue(extreme.getScore() <= 10.0 && extreme.getScore() >= 0.0);

        ThreatEntry nothing = ThreatEngine.evaluate(ThreatInput.builder("Min")
                .stats(stats(0, 0, 0)).gear(Gear.EMPTY).bedIntact(false).deathsThisGame(9).build());
        assertTrue(nothing.getScore() >= 0.0 && nothing.getScore() <= 10.0);
    }

    @Test
    void rankingPutsTheBiggestThreatFirstAndExcludesYou() {
        List<ThreatInput> lobby = Arrays.asList(
                ThreatInput.builder("Weak").stats(stats(10, 50, 100)).gear(Gear.EMPTY).build(),
                ThreatInput.builder("Strong").stats(stats(500, 9000, 400)).gear(FULL_DIAMOND).build(),
                ThreatInput.builder("Me").stats(stats(999, 99999, 1)).gear(FULL_DIAMOND).self(true).build());

        List<ThreatEntry> ranked = ThreatEngine.rank(lobby, ThreatWeights.DEFAULT);
        assertEquals(2, ranked.size(), "your own player should not be listed");
        assertEquals("Strong", ranked.get(0).getName());
        assertEquals("Weak", ranked.get(1).getName());
    }

    @Test
    void tiesResolveByNameSoTheListDoesNotShuffleBetweenFrames() {
        List<ThreatInput> lobby = Arrays.asList(
                ThreatInput.builder("Zeta").stats(stats(100, 1000, 500)).gear(STARTING).build(),
                ThreatInput.builder("Alpha").stats(stats(100, 1000, 500)).gear(STARTING).build());
        List<ThreatEntry> ranked = ThreatEngine.rank(lobby, ThreatWeights.DEFAULT);
        assertEquals("Alpha", ranked.get(0).getName());
    }

    @Test
    void weightsChangeTheOrdering() {
        ThreatInput statsPlayer = ThreatInput.builder("Stats")
                .stats(stats(500, 9000, 400)).gear(Gear.EMPTY).build();
        ThreatInput gearPlayer = ThreatInput.builder("Gear")
                .stats(stats(5, 10, 40)).gear(FULL_DIAMOND).build();
        List<ThreatInput> lobby = Arrays.asList(statsPlayer, gearPlayer);

        assertEquals("Stats", ThreatEngine.rank(lobby, new ThreatWeights(1.0, 0.05, 0.05)).get(0).getName());
        assertEquals("Gear", ThreatEngine.rank(lobby, new ThreatWeights(0.05, 1.0, 0.05)).get(0).getName());
    }

    @Test
    void zeroingEveryWeightDoesNotDivideByZero() {
        ThreatEntry entry = ThreatEngine.evaluate(ThreatInput.builder("P")
                .stats(stats(100, 1000, 500)).gear(FULL_DIAMOND).build(), new ThreatWeights(0, 0, 0));
        assertFalse(Double.isNaN(entry.getScore()));
        assertTrue(entry.getScore() > 0.0);
    }

    @Test
    void gearShorthandReadsAtAGlance() {
        assertEquals("D/D", FULL_DIAMOND.shorthand());
        assertEquals("-/-", Gear.EMPTY.shorthand());
        assertEquals("L/W", STARTING.shorthand());
    }
}
