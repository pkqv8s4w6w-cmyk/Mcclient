package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachAnalysisTest {

    /** A player-sized box centred on the origin's x/z, standing on y = 0. */
    private static double distanceFrom(double x, double y, double z) {
        return ReachAnalysis.distanceToBox(x, y, z, -0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
    }

    @Test
    void measuresToTheNearestFaceNotTheCentre() {
        // Four blocks along x from the centre is 3.7 from the box's face. Measuring to the centre
        // would read every honest hit about half a block long.
        assertEquals(3.7, distanceFrom(4.0, 1.0, 0.0), 1e-9);
    }

    @Test
    void aPointInsideTheBoxIsZeroAway() {
        assertEquals(0.0, distanceFrom(0.0, 1.0, 0.0), 1e-9);
    }

    @Test
    void diagonalDistancesCombineEveryAxis() {
        double expected = Math.sqrt(0.7 * 0.7 + 0.7 * 0.7);
        assertEquals(expected, distanceFrom(1.0, 1.0, 1.0), 1e-9);
    }

    @Test
    void heightAboveTheBoxCounts() {
        assertEquals(0.2, distanceFrom(0.0, 2.0, 0.0), 1e-9);
    }

    @Test
    void ordinaryHitsAreNotFlagged() {
        double[] normal = {2.6, 2.9, 3.0, 2.7, 3.1, 2.8, 3.05};
        assertFalse(ReachAnalysis.analyse(normal, 3.4).isSuspicious());
    }

    @Test
    void oneLongReadingFromLagIsNotEnough() {
        // A single spike must not convict; the median ignores it.
        double[] mostlyNormal = {2.8, 2.9, 3.0, 6.2, 2.7, 2.9, 3.0};
        ReachAnalysis.Result result = ReachAnalysis.analyse(mostlyNormal, 3.4);
        assertFalse(result.isSuspicious());
        assertEquals(6.2, result.getWorst(), 1e-9);
    }

    @Test
    void aConsistentlyLongReachIsFlagged() {
        double[] extended = {3.7, 3.8, 3.9, 3.75, 3.85, 3.95, 3.8};
        ReachAnalysis.Result result = ReachAnalysis.analyse(extended, 3.4);
        assertTrue(result.isSuspicious());
        assertTrue(result.getMedian() > 3.7);
    }

    @Test
    void tooFewHitsYieldNoVerdict() {
        assertFalse(ReachAnalysis.analyse(new double[]{4.0, 4.0}, 3.4).isSuspicious());
        assertFalse(ReachAnalysis.analyse(null, 3.4).isSuspicious());
    }

    @Test
    void theAllowanceSitsAboveVanillaToAbsorbLagCompensation() {
        double[] slightlyLong = {3.2, 3.25, 3.3, 3.2, 3.15, 3.3, 3.2};
        assertTrue(ReachAnalysis.analyse(slightlyLong, ReachAnalysis.VANILLA_REACH).isSuspicious(),
                "measured against bare vanilla reach this looks illegal");
        assertFalse(ReachAnalysis.analyse(slightlyLong, 3.4).isSuspicious(),
                "with a realistic allowance it is an ordinary rewound hit");
    }
}
