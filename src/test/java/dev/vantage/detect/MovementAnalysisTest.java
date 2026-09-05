package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only backwards-sprinting is left here. The speed, flight and jump-height checks were removed
 * because they read a remote player's interpolated position as though it were a measurement; see
 * {@link MovementAnalysis} for why no threshold could have fixed them.
 */
class MovementAnalysisTest {

    private static final int OMNI_ALLOWED = 10;

    /** Positions arrive rounded to 1/32 of a block; every fixture goes through the same rounding. */
    private static double quantise(double value) {
        return Math.round(value * 32.0) / 32.0;
    }

    private static List<MovementSample> walk(int ticks, double perTick, boolean sprinting) {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        Random random = new Random(3);
        double x = 0.0;
        for (int i = 0; i < ticks; i++) {
            // A little scatter, as real movement has.
            x += perTick * (0.92 + random.nextDouble() * 0.16);
            samples.add(new MovementSample(quantise(x), 64.0, 0.0, true, sprinting, 1.0, false));
        }
        return samples;
    }

    @Test
    void sprintingForwardsIsFine() {
        assertFalse(MovementAnalysis.omniSprint(walk(60, 0.28, true), OMNI_ALLOWED).isSuspicious());
    }

    @Test
    void sprintingBackwardsIsFlagged() {
        // Impossible in vanilla 1.8, so this has almost no false-positive surface.
        List<MovementSample> samples = new ArrayList<MovementSample>();
        for (int i = 0; i < 60; i++) {
            samples.add(new MovementSample(i * 0.25, 64.0, 0.0, true, true, -1.0, false));
        }
        assertTrue(MovementAnalysis.omniSprint(samples, OMNI_ALLOWED).isSuspicious());
    }

    @Test
    void beingKnockedBackwardsBrieflyIsNotFlagged() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        for (int i = 0; i < 60; i++) {
            // Five ticks of being pushed backwards with the sprint flag still set.
            double dot = (i >= 20 && i < 25) ? -1.0 : 1.0;
            samples.add(new MovementSample(i * 0.25, 64.0, 0.0, true, true, dot, false));
        }
        assertFalse(MovementAnalysis.omniSprint(samples, OMNI_ALLOWED).isSuspicious());
    }

    @Test
    void standingStillWhileSprintFlaggedIsNotFlagged() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        for (int i = 0; i < 60; i++) {
            samples.add(new MovementSample(0.0, 64.0, 0.0, true, true, -1.0, false));
        }
        assertFalse(MovementAnalysis.omniSprint(samples, OMNI_ALLOWED).isSuspicious(),
                "no movement means no direction to judge");
    }

    @Test
    void teleportsAreSkippedRatherThanMeasured() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        for (int i = 0; i < 60; i++) {
            boolean teleported = i == 30;
            samples.add(new MovementSample(teleported ? 900.0 : i * 0.25, 64.0, 0.0,
                    true, true, i >= 25 && i <= 35 ? -1.0 : 1.0, teleported));
        }
        assertFalse(MovementAnalysis.omniSprint(samples, OMNI_ALLOWED).isSuspicious(),
                "a server reposition must break the run rather than extend it");
    }

    @Test
    void tooFewSamplesYieldNothing() {
        assertFalse(MovementAnalysis.omniSprint(walk(5, 0.25, true), OMNI_ALLOWED).isSuspicious());
        assertFalse(MovementAnalysis.omniSprint(null, OMNI_ALLOWED).isSuspicious());
    }
}
