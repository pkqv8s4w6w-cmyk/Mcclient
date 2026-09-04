package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickAnalysisTest {

    /** Human clicking rarely drops below this spread, even at high rates. */
    private static final double NORMAL_SPREAD_LIMIT = 5.0;

    /** Even intervals, as a timer produces. */
    private static long[] machine(int count, long intervalMillis) {
        long[] times = new long[count];
        for (int i = 0; i < count; i++) {
            times[i] = 1_000_000L + i * intervalMillis;
        }
        return times;
    }

    /** Intervals scattered around a mean, as a hand produces. */
    private static long[] human(int count, long meanMillis, long jitterMillis, long seed) {
        Random random = new Random(seed);
        long[] times = new long[count];
        long now = 1_000_000L;
        for (int i = 0; i < count; i++) {
            times[i] = now;
            now += Math.max(1L, meanMillis + (long) ((random.nextDouble() * 2 - 1) * jitterMillis));
        }
        return times;
    }

    @Test
    void aPerfectlyEvenSequenceIsFlagged() {
        ClickAnalysis.Result result = ClickAnalysis.analyse(machine(40, 80), NORMAL_SPREAD_LIMIT);
        assertTrue(result.isSuspicious());
        assertTrue(result.getConfidence() > 0.9, "confidence was " + result.getConfidence());
        assertEquals(12.5, result.getClicksPerSecond(), 0.1);
    }

    @Test
    void ordinaryHumanClickingIsNotFlagged() {
        ClickAnalysis.Result result = ClickAnalysis.analyse(human(40, 90, 25, 1), NORMAL_SPREAD_LIMIT);
        assertFalse(result.isSuspicious(),
                "spread was " + result.getStandardDeviationMillis() + "ms");
    }

    @Test
    void fastButterflyClickingIsNotFlaggedJustForBeingFast() {
        // Around 16 clicks a second by hand is normal and legitimate. Judging on rate alone would
        // catch this, which is exactly the mistake worth avoiding.
        ClickAnalysis.Result result = ClickAnalysis.analyse(human(60, 62, 16, 7), NORMAL_SPREAD_LIMIT);
        assertTrue(result.getClicksPerSecond() > 14.0, "rate was " + result.getClicksPerSecond());
        assertFalse(result.isSuspicious(), "fast but scattered clicking must pass");
    }

    @Test
    void anAutoclickerWithSlightRandomisationIsStillFlagged() {
        ClickAnalysis.Result result = ClickAnalysis.analyse(human(50, 85, 3, 3), NORMAL_SPREAD_LIMIT);
        assertTrue(result.isSuspicious(), "spread was " + result.getStandardDeviationMillis() + "ms");
    }

    @Test
    void aShortBurstIsNotEnoughToJudge() {
        assertFalse(ClickAnalysis.analyse(machine(6, 80), NORMAL_SPREAD_LIMIT).isSuspicious());
        assertEquals(0.0, ClickAnalysis.analyse(null, NORMAL_SPREAD_LIMIT).getConfidence());
    }

    @Test
    void slowDeliberateClickingIsIgnored() {
        // Gaps this long are dominated by pauses, not by clicking technique.
        assertFalse(ClickAnalysis.analyse(machine(40, 400), NORMAL_SPREAD_LIMIT).isSuspicious());
    }

    @Test
    void theThresholdTradesSensitivityAgainstFalsePositives() {
        long[] borderline = human(40, 90, 10, 11);
        assertTrue(ClickAnalysis.analyse(borderline, 14.0).isSuspicious(),
                "a loose threshold catches more, including borderline humans");
        assertFalse(ClickAnalysis.analyse(borderline, 4.0).isSuspicious(),
                "a tight threshold only catches near-perfect timing");
    }

    @Test
    void nonIncreasingTimestampsAreRejectedRatherThanScored() {
        long[] broken = machine(40, 80);
        broken[20] = broken[19];
        assertFalse(ClickAnalysis.analyse(broken, NORMAL_SPREAD_LIMIT).isSuspicious());
    }
}
