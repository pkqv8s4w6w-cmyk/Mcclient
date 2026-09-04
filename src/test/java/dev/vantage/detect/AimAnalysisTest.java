package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimAnalysisTest {

    private static final double SNAP_DEGREES = 30.0;
    private static final double LOCK_DEGREES = 3.0;

    /** Rounds to the resolution other players' rotations actually arrive at. */
    private static double quantise(double degrees) {
        return Math.round(degrees / AimAnalysis.PACKET_ROTATION_STEP) * AimAnalysis.PACKET_ROTATION_STEP;
    }

    @Test
    void aViewThatDriftsAroundATargetIsNotFlagged() {
        Random random = new Random(4);
        int samples = 60;
        double[] deltas = new double[samples];
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            deltas[i] = quantise((random.nextDouble() * 2 - 1) * 8.0);
            // A person overshoots and corrects, so the angle wanders well past the lock cone.
            angles[i] = 2.0 + random.nextDouble() * 20.0;
        }
        assertFalse(AimAnalysis.analyse(deltas, angles, SNAP_DEGREES, LOCK_DEGREES).isSuspicious());
    }

    @Test
    void aViewGluedToATargetIsFlagged() {
        int samples = 60;
        double[] deltas = new double[samples];
        double[] angles = new double[samples];
        Random random = new Random(9);
        for (int i = 0; i < samples; i++) {
            deltas[i] = quantise((random.nextDouble() * 2 - 1) * 6.0);
            angles[i] = 0.4; // never drifts off, through movement that should disturb it
        }
        AimAnalysis.Result result = AimAnalysis.analyse(deltas, angles, SNAP_DEGREES, LOCK_DEGREES);
        assertTrue(result.isSuspicious());
        assertTrue(result.getLockedFraction() > 0.95);
    }

    @Test
    void repeatedSingleTickTurnsOntoATargetAreFlagged() {
        int samples = 40;
        double[] deltas = new double[samples];
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            deltas[i] = 1.0;
            angles[i] = 40.0; // facing away most of the time
        }
        // Four large turns that each land on a target.
        for (int i : new int[]{5, 12, 21, 30}) {
            deltas[i] = 85.0;
            angles[i] = 0.5;
        }
        AimAnalysis.Result result = AimAnalysis.analyse(deltas, angles, SNAP_DEGREES, LOCK_DEGREES);
        assertEquals(4, result.getSnaps());
        assertTrue(result.isSuspicious());
    }

    @Test
    void aSingleLuckyFlickIsNotEnough() {
        int samples = 40;
        double[] deltas = new double[samples];
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            deltas[i] = 2.0;
            angles[i] = 30.0;
        }
        deltas[10] = 90.0;
        angles[10] = 1.0;
        assertFalse(AimAnalysis.analyse(deltas, angles, SNAP_DEGREES, LOCK_DEGREES).isSuspicious());
    }

    @Test
    void quantisationAloneDoesNotLookLikeCheating() {
        // Every rotation a client receives is already rounded to about 1.4 degrees. That rounding
        // must not be mistaken for inhuman precision.
        Random random = new Random(21);
        int samples = 80;
        double[] deltas = new double[samples];
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            deltas[i] = quantise((random.nextDouble() * 2 - 1) * 12.0);
            angles[i] = quantise(4.0 + random.nextDouble() * 15.0);
        }
        assertFalse(AimAnalysis.analyse(deltas, angles, SNAP_DEGREES, LOCK_DEGREES).isSuspicious());
    }

    @Test
    void ticksWithNoTargetDoNotDiluteTheMeasurement() {
        int samples = 60;
        double[] deltas = new double[samples];
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            deltas[i] = 1.0;
            // Half the window facing nothing at all; the other half locked on.
            angles[i] = i % 2 == 0 ? 175.0 : 0.3;
        }
        AimAnalysis.Result result = AimAnalysis.analyse(deltas, angles, SNAP_DEGREES, LOCK_DEGREES);
        assertTrue(result.isSuspicious(),
                "locked fraction should be measured over ticks with a target, not the whole window");
    }

    @Test
    void tooLittleDataYieldsNothing() {
        assertFalse(AimAnalysis.analyse(new double[5], new double[5], SNAP_DEGREES, LOCK_DEGREES).isSuspicious());
        assertFalse(AimAnalysis.analyse(null, null, SNAP_DEGREES, LOCK_DEGREES).isSuspicious());
    }

    @Test
    void mismatchedSeriesAreRejected() {
        assertFalse(AimAnalysis.analyse(new double[30], new double[20], SNAP_DEGREES, LOCK_DEGREES).isSuspicious());
    }
}
