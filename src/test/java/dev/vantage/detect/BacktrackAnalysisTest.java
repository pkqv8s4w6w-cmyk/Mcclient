package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktrackAnalysisTest {

    private static final double ALLOWED = ReachAnalysis.DEFAULT_ALLOWED_REACH;

    /**
     * Somebody on a genuinely bad connection.
     *
     * <p>Their hits land looking long and were legal a moment ago — the same shape backtrack
     * leaves. What separates them is that real latency wanders: jitter, dropped packets and server
     * tick timing put every hit at a different delay.
     */
    private static List<HitSample> laggyButHonest(int count, long seed) {
        Random random = new Random(seed);
        List<HitSample> hits = new ArrayList<HitSample>();
        for (int i = 0; i < count; i++) {
            int delay = 1 + random.nextInt(12);
            hits.add(new HitSample(4.0 + random.nextDouble(), 2.6 + random.nextDouble() * 0.3, delay));
        }
        return hits;
    }

    /**
     * Somebody running a backtrack module.
     *
     * <p>It holds incoming packets for a configured length of time, so every hit comes from close
     * to the same point in the past. That consistency is the tell, not the size of the delay.
     */
    private static List<HitSample> backtracking(int count, int heldTicks, long seed) {
        Random random = new Random(seed);
        List<HitSample> hits = new ArrayList<HitSample>();
        for (int i = 0; i < count; i++) {
            int delay = heldTicks + (random.nextBoolean() ? 0 : 1);
            hits.add(new HitSample(4.2 + random.nextDouble() * 0.6,
                    2.5 + random.nextDouble() * 0.4, delay));
        }
        return hits;
    }

    // -- the failure that matters -------------------------------------------------------------

    @Test
    void aLaggyHonestPlayerIsNotFlagged() {
        for (long seed = 1; seed <= 8; seed++) {
            BacktrackAnalysis.Result result = BacktrackAnalysis.analyse(laggyButHonest(16, seed), ALLOWED);
            assertFalse(result.isSuspicious(),
                    "seed " + seed + ": median " + result.getMedianTicksAgo()
                            + " ticks, spread " + result.getSpreadTicks());
        }
    }

    @Test
    void ordinaryCloseRangeFightingSaysNothing() {
        // Nothing needs explaining, so there is nothing to explain it with.
        List<HitSample> hits = new ArrayList<HitSample>();
        for (int i = 0; i < 16; i++) {
            hits.add(new HitSample(2.7, 2.6, 1));
        }
        assertFalse(BacktrackAnalysis.analyse(hits, ALLOWED).isSuspicious());
    }

    @Test
    void plainReachIsLeftToTheReachCheck() {
        // Both distances long means the hit was never legal from anywhere, which is reach. This
        // check must not also claim it, or one cheat would corroborate itself.
        List<HitSample> hits = new ArrayList<HitSample>();
        for (int i = 0; i < 16; i++) {
            hits.add(new HitSample(4.5, 4.4, 6));
        }
        assertFalse(BacktrackAnalysis.analyse(hits, ALLOWED).isSuspicious());
    }

    @Test
    void oneTickOfInterpolationLagIsNotBacktrack() {
        // Everybody's hits are a tick or two behind; that is how the game works.
        List<HitSample> hits = new ArrayList<HitSample>();
        for (int i = 0; i < 16; i++) {
            hits.add(new HitSample(3.8, 2.8, 1));
        }
        assertFalse(BacktrackAnalysis.analyse(hits, ALLOWED).isSuspicious());
    }

    // -- what it should catch -----------------------------------------------------------------

    @Test
    void aConsistentDelayIsFlagged() {
        BacktrackAnalysis.Result result = BacktrackAnalysis.analyse(backtracking(16, 7, 3), ALLOWED);
        assertTrue(result.isSuspicious(),
                "median " + result.getMedianTicksAgo() + ", spread " + result.getSpreadTicks());
        assertTrue(result.getMedianTicksAgo() >= 6.0, "it should report how far back they are hitting");
    }

    @Test
    void itCatchesTheRangeOfBufferSettingsPeopleUse() {
        for (int held : new int[]{4, 6, 8, 12}) {
            assertTrue(BacktrackAnalysis.analyse(backtracking(16, held, held), ALLOWED).isSuspicious(),
                    "held " + held + " ticks");
        }
    }

    @Test
    void aTighterDelayReadsAsMoreCertainThanALooserOne() {
        List<HitSample> exact = new ArrayList<HitSample>();
        for (int i = 0; i < 16; i++) {
            exact.add(new HitSample(4.2, 2.7, 7));
        }
        double perfect = BacktrackAnalysis.analyse(exact, ALLOWED).getConfidence();
        double wobbly = BacktrackAnalysis.analyse(backtracking(16, 7, 9), ALLOWED).getConfidence();
        assertTrue(perfect > wobbly, perfect + " vs " + wobbly);
    }

    // -- edges --------------------------------------------------------------------------------

    @Test
    void tooFewHitsSayNothing() {
        assertFalse(BacktrackAnalysis.analyse(backtracking(6, 7, 1), ALLOWED).isSuspicious());
        assertFalse(BacktrackAnalysis.analyse(null, ALLOWED).isSuspicious());
    }

    @Test
    void aMixOfExplainedAndUnexplainedHitsSaysNothing() {
        // Half their long hits were legal a moment ago and half were never legal at all. That is
        // not a pattern of anything, and guessing at it is how a check starts inventing cheats.
        List<HitSample> hits = new ArrayList<HitSample>();
        for (int i = 0; i < 20; i++) {
            hits.add(i % 2 == 0
                    ? new HitSample(4.2, 2.7, 7)
                    : new HitSample(4.2, 4.1, 7));
        }
        assertFalse(BacktrackAnalysis.analyse(hits, ALLOWED).isSuspicious());
    }
}
