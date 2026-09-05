package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementAnalysisTest {

    private static final double SPEED_ALLOWED = 0.45;
    private static final int HOVER_ALLOWED = 20;
    private static final int OMNI_ALLOWED = 10;
    private static final double JUMP_ALLOWED = 2.0;

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

    // -- speed ------------------------------------------------------------------------------

    @Test
    void ordinarySprintingIsNotFlagged() {
        assertFalse(MovementAnalysis.speed(walk(60, 0.28, true), SPEED_ALLOWED).isSuspicious());
    }

    @Test
    void sprintJumpingIsNotFlagged() {
        // Sprint-jumping averages well under its peak, which is why the check uses the median.
        assertFalse(MovementAnalysis.speed(walk(60, 0.36, true), SPEED_ALLOWED).isSuspicious());
    }

    @Test
    void sustainedImpossibleSpeedIsFlagged() {
        MovementAnalysis.Result result = MovementAnalysis.speed(walk(60, 0.75, true), SPEED_ALLOWED);
        assertTrue(result.isSuspicious(), "median was " + result.getMeasured());
    }

    @Test
    void oneLagCorrectionDoesNotConvict() {
        List<MovementSample> samples = walk(60, 0.28, true);
        // A single huge jump, as a rubber-band produces.
        samples.set(30, new MovementSample(400.0, 64.0, 0.0, true, true, 1.0, false));
        assertFalse(MovementAnalysis.speed(samples, SPEED_ALLOWED).isSuspicious());
    }

    @Test
    void teleportsAreSkippedRatherThanMeasured() {
        List<MovementSample> samples = walk(60, 0.28, true);
        // A server reposition: marked, and both differences touching it must be discarded.
        samples.set(30, new MovementSample(900.0, 64.0, 0.0, true, true, 1.0, true));
        assertFalse(MovementAnalysis.speed(samples, SPEED_ALLOWED).isSuspicious());
    }

    @Test
    void tooFewSamplesYieldNothing() {
        assertFalse(MovementAnalysis.speed(walk(5, 2.0, true), SPEED_ALLOWED).isSuspicious());
        assertFalse(MovementAnalysis.speed(null, SPEED_ALLOWED).isSuspicious());
    }

    // -- hover ------------------------------------------------------------------------------

    @Test
    void anOrdinaryJumpArcIsNotFlagged() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        for (int i = 0; i < 60; i++) {
            // Repeated jumps: up for a few ticks, then down, then grounded.
            int phase = i % 12;
            double y = 64.0 + (phase <= 5 ? phase * 0.25 : (11 - phase) * 0.25);
            boolean ground = phase == 0;
            samples.add(new MovementSample(i * 0.2, quantise(y), 0.0, ground, true, 1.0, false));
        }
        assertFalse(MovementAnalysis.hover(samples, HOVER_ALLOWED).isSuspicious());
    }

    @Test
    void hangingInTheAirIsFlagged() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        for (int i = 0; i < 60; i++) {
            samples.add(new MovementSample(i * 0.2, 70.0, 0.0, false, false, 1.0, false));
        }
        MovementAnalysis.Result result = MovementAnalysis.hover(samples, HOVER_ALLOWED);
        assertTrue(result.isSuspicious(), "longest run was " + result.getMeasured());
    }

    @Test
    void aSlowRiseWhileAirborneIsFlagged() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        for (int i = 0; i < 60; i++) {
            samples.add(new MovementSample(0.0, quantise(70.0 + i * 0.1), 0.0, false, false, 1.0, false));
        }
        assertTrue(MovementAnalysis.hover(samples, HOVER_ALLOWED).isSuspicious());
    }

    @Test
    void quantisationNoiseWhileFallingDoesNotReadAsHovering() {
        // Falling positions rounded to 1/32 can repeat a value between ticks; the tolerance in the
        // check exists for exactly that and must not turn a fall into a hover.
        List<MovementSample> samples = new ArrayList<MovementSample>();
        double y = 90.0;
        for (int i = 0; i < 60; i++) {
            y -= 0.28;
            samples.add(new MovementSample(0.0, quantise(y), 0.0, false, false, 1.0, false));
        }
        assertFalse(MovementAnalysis.hover(samples, HOVER_ALLOWED).isSuspicious());
    }

    // -- jump height ------------------------------------------------------------------------

    @Test
    void aVanillaJumpIsNotFlagged() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        samples.add(new MovementSample(0, 64.0, 0, true, false, 1.0, false));
        for (int i = 1; i < 40; i++) {
            double height = Math.min(MovementAnalysis.VANILLA_JUMP_HEIGHT, i * 0.2);
            samples.add(new MovementSample(0, quantise(64.0 + height), 0, false, false, 1.0, false));
        }
        assertFalse(MovementAnalysis.jumpHeight(samples, JUMP_ALLOWED).isSuspicious());
    }

    @Test
    void leapingFarHigherThanPossibleIsFlagged() {
        List<MovementSample> samples = new ArrayList<MovementSample>();
        samples.add(new MovementSample(0, 64.0, 0, true, false, 1.0, false));
        for (int i = 1; i < 40; i++) {
            samples.add(new MovementSample(0, quantise(64.0 + Math.min(4.0, i * 0.3)), 0, false, false, 1.0, false));
        }
        assertTrue(MovementAnalysis.jumpHeight(samples, JUMP_ALLOWED).isSuspicious());
    }

    // -- omnisprint -------------------------------------------------------------------------

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
}
