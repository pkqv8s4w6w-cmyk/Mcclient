package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldAnalysisTest {

    private static final double LEVEL_PITCH = 30.0;
    private static final double BEHIND_ANGLE = 60.0;

    private static double[] filled(int count, double value, double jitter, long seed) {
        Random random = new Random(seed);
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = value + (random.nextDouble() * 2 - 1) * jitter;
        }
        return out;
    }

    @Test
    void ordinaryBridgingIsNotFlagged() {
        // Aiming down at the block you place, which is how everyone crosses a gap. Flagging this
        // would flag the entire lobby.
        double[] pitches = filled(20, 55.0, 10.0, 1);
        double[] angles = filled(20, 20.0, 12.0, 2);
        assertFalse(ScaffoldAnalysis.analyse(pitches, angles, LEVEL_PITCH, BEHIND_ANGLE).isSuspicious());
    }

    @Test
    void placingBehindYourselfWhileLookingLevelIsFlagged() {
        double[] pitches = filled(20, 8.0, 6.0, 3);
        double[] angles = filled(20, 140.0, 15.0, 4);
        ScaffoldAnalysis.Result result =
                ScaffoldAnalysis.analyse(pitches, angles, LEVEL_PITCH, BEHIND_ANGLE);
        assertTrue(result.isSuspicious(), "fraction was " + result.getAutomatedFraction());
    }

    @Test
    void lookingLevelWhilePlacingWhereYouLookIsNotEnough() {
        // Level view but the block is in front: placing a wall, not bridging behind yourself.
        double[] pitches = filled(20, 5.0, 5.0, 5);
        double[] angles = filled(20, 15.0, 10.0, 6);
        assertFalse(ScaffoldAnalysis.analyse(pitches, angles, LEVEL_PITCH, BEHIND_ANGLE).isSuspicious());
    }

    @Test
    void aimingDownAtBlocksBehindYouIsNotEnough() {
        // Backwards bridging by hand: the block is behind, but the view is aimed down at it.
        double[] pitches = filled(20, 60.0, 8.0, 7);
        double[] angles = filled(20, 120.0, 10.0, 8);
        assertFalse(ScaffoldAnalysis.analyse(pitches, angles, LEVEL_PITCH, BEHIND_ANGLE).isSuspicious());
    }

    @Test
    void anOccasionalAutomatedLookingPlacementIsNotEnough() {
        double[] pitches = filled(20, 55.0, 8.0, 9);
        double[] angles = filled(20, 25.0, 8.0, 10);
        // Three placements out of twenty that look automated.
        for (int i : new int[]{2, 9, 15}) {
            pitches[i] = 5.0;
            angles[i] = 150.0;
        }
        assertFalse(ScaffoldAnalysis.analyse(pitches, angles, LEVEL_PITCH, BEHIND_ANGLE).isSuspicious());
    }

    @Test
    void tooFewPlacementsYieldNothing() {
        double[] pitches = filled(4, 5.0, 1.0, 11);
        double[] angles = filled(4, 150.0, 1.0, 12);
        assertFalse(ScaffoldAnalysis.analyse(pitches, angles, LEVEL_PITCH, BEHIND_ANGLE).isSuspicious());
    }

    @Test
    void mismatchedOrMissingInputsAreRejected() {
        assertFalse(ScaffoldAnalysis.analyse(new double[10], new double[9], LEVEL_PITCH, BEHIND_ANGLE).isSuspicious());
        assertFalse(ScaffoldAnalysis.analyse(null, null, LEVEL_PITCH, BEHIND_ANGLE).isSuspicious());
    }

    @Test
    void reportsHowManyPlacementsItJudged() {
        double[] pitches = filled(12, 5.0, 2.0, 13);
        double[] angles = filled(12, 150.0, 5.0, 14);
        assertTrue(ScaffoldAnalysis.analyse(pitches, angles, LEVEL_PITCH, BEHIND_ANGLE)
                .getPlacements() == 12);
    }
}
