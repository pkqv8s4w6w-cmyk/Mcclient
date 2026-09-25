package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickAnalysisTest {

    private static final double SPREAD = ClickAnalysis.DEFAULT_MAX_SPREAD_MILLIS;

    /** A hand: the interval wanders, and every so often the rhythm breaks entirely. */
    private static long[] byHand(int clicks, double meanMillis, double jitterMillis, long seed) {
        Random random = new Random(seed);
        long[] times = new long[clicks];
        long now = 1_000_000L;
        for (int i = 0; i < clicks; i++) {
            times[i] = now;
            double gap = meanMillis + random.nextGaussian() * jitterMillis;
            // Roughly one click in fifteen, a person hesitates, adjusts their grip, or looks away.
            if (random.nextInt(15) == 0) {
                gap += 60.0 + random.nextDouble() * 120.0;
            }
            now += Math.max(1L, Math.round(gap));
        }
        return times;
    }

    /** A timer, optionally with the jitter clickers add to look human. */
    private static long[] byTimer(int clicks, double meanMillis, double jitterShare, long seed) {
        Random random = new Random(seed);
        long[] times = new long[clicks];
        long now = 1_000_000L;
        for (int i = 0; i < clicks; i++) {
            times[i] = now;
            double gap = meanMillis * (1.0 + (random.nextDouble() * 2.0 - 1.0) * jitterShare);
            now += Math.max(1L, Math.round(gap));
        }
        return times;
    }

    // -- the failure that matters -------------------------------------------------------------

    @Test
    void fastHumanClickingIsNotFlagged() {
        // Butterfly and drag clicking reach 12 to 16 a second legitimately. The rate is not the
        // tell and never was.
        for (long seed = 1; seed <= 6; seed++) {
            ClickAnalysis.Result result = ClickAnalysis.analyse(byHand(60, 70.0, 15.0, seed), SPREAD);
            assertFalse(result.isSuspicious(), "seed " + seed + ": " + result.getClicksPerSecond()
                    + " cps at " + result.getStandardDeviationMillis() + "ms spread");
        }
    }

    @Test
    void averageHumanClickingIsNotFlagged() {
        for (long seed = 1; seed <= 6; seed++) {
            assertFalse(ClickAnalysis.analyse(byHand(60, 110.0, 22.0, seed), SPREAD).isSuspicious());
        }
    }

    @Test
    void aSteadyHandWithOccasionalPausesIsNotFlagged() {
        // The rhythm is tight, but a person still stops now and then. A timer never does, and that
        // is what the outlier count is for.
        long[] times = byTimer(60, 90.0, 0.03, 4);
        // Insert three real pauses, the kind a hand makes.
        for (int i = 15; i < times.length; i++) {
            times[i] += 250L;
        }
        for (int i = 30; i < times.length; i++) {
            times[i] += 300L;
        }
        for (int i = 45; i < times.length; i++) {
            times[i] += 200L;
        }
        assertFalse(ClickAnalysis.analyse(times, SPREAD).isSuspicious());
    }

    // -- what it should catch -----------------------------------------------------------------

    @Test
    void aPerfectlyEvenTimerIsFlagged() {
        ClickAnalysis.Result result = ClickAnalysis.analyse(byTimer(60, 90.0, 0.0, 1), SPREAD);
        assertTrue(result.isSuspicious());
        assertEquals(1.0, result.getConfidence(), 0.05, "no variation at all is as sure as it gets");
    }

    @Test
    void aTimerWithHumanisingJitterIsStillFlagged() {
        // Clickers advertise about ten percent randomisation as enough to pass for a person. At a
        // dozen clicks a second that is only a few milliseconds of spread.
        for (long seed = 1; seed <= 5; seed++) {
            ClickAnalysis.Result result = ClickAnalysis.analyse(byTimer(60, 85.0, 0.10, seed), SPREAD);
            assertTrue(result.isSuspicious(),
                    "seed " + seed + " spread " + result.getStandardDeviationMillis());
        }
    }

    // -- robustness ---------------------------------------------------------------------------

    @Test
    void oneDroppedPacketDoesNotHideAClicker() {
        // A stretched interval used to dominate the standard deviation, so any connection that
        // hiccupped blinded the check on exactly the players it should have caught.
        long[] times = byTimer(60, 90.0, 0.02, 2);
        for (int i = 25; i < times.length; i++) {
            times[i] += 400L;
        }
        assertTrue(ClickAnalysis.analyse(times, SPREAD).isSuspicious());
    }

    @Test
    void oneDuplicateTimestampDoesNotBlankTheWholeWindow() {
        // Two swings in the same millisecond is a packet artefact. The old version threw away the
        // entire sample over it, which lost seconds of evidence at a time.
        long[] times = byTimer(60, 90.0, 0.02, 3);
        times[20] = times[19];
        ClickAnalysis.Result result = ClickAnalysis.analyse(times, SPREAD);
        assertTrue(result.isSuspicious());
        assertTrue(result.getSamples() >= 55, "only the one bad interval should be dropped");
    }

    // -- edges --------------------------------------------------------------------------------

    @Test
    void slowDeliberateClickingSaysNothing() {
        // Below a handful a second the gaps are pauses, not a rhythm, and their evenness is
        // meaningless either way.
        assertFalse(ClickAnalysis.analyse(byTimer(60, 400.0, 0.0, 1), SPREAD).isSuspicious());
    }

    @Test
    void aShortBurstSaysNothing() {
        assertFalse(ClickAnalysis.analyse(byTimer(15, 90.0, 0.0, 1), SPREAD).isSuspicious(),
                "under a couple of seconds of clicking is not a pattern");
    }

    @Test
    void malformedInputIsSafe() {
        assertFalse(ClickAnalysis.analyse(null, SPREAD).isSuspicious());
        assertFalse(ClickAnalysis.analyse(new long[0], SPREAD).isSuspicious());
        long[] identical = new long[60];
        java.util.Arrays.fill(identical, 5L);
        assertFalse(ClickAnalysis.analyse(identical, SPREAD).isSuspicious(),
                "no usable gaps at all means no verdict, not a certain one");
    }
}
