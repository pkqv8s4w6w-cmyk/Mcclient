package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimAnalysisTest {

    private static final double CONE = AimAnalysis.DEFAULT_CONE_DEGREES;

    /** A recorded fight: what they turned each tick, and where the target was afterwards. */
    private static final class Fight {
        final double[] deltas;
        final double[] errors;

        Fight(List<Double> deltas, List<Double> errors) {
            this.deltas = unbox(deltas);
            this.errors = unbox(errors);
        }

        private static double[] unbox(List<Double> values) {
            double[] out = new double[values.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = values.get(i);
            }
            return out;
        }
    }

    /**
     * Rotations arrive as one byte per axis, so every angle a client ever sees is a multiple of
     * 360/256. Every fixture goes through the same rounding, because a check that only works on
     * clean numbers does not work at all.
     */
    private static double quantise(double degrees) {
        return Math.round(degrees / AimAnalysis.PACKET_ROTATION_STEP) * AimAnalysis.PACKET_ROTATION_STEP;
    }

    /**
     * Somebody aiming with their hand.
     *
     * <p>They swing toward the target at a rate that varies wildly, frequently pass it and correct
     * back, and while tracking they give ground constantly as the target moves.
     */
    private static Fight byHand(int engagements, long seed) {
        Random random = new Random(seed);
        List<Double> deltas = new ArrayList<Double>();
        List<Double> errors = new ArrayList<Double>();

        for (int engagement = 0; engagement < engagements; engagement++) {
            double error = (random.nextBoolean() ? 1 : -1) * (25.0 + random.nextDouble() * 35.0);
            int guard = 0;
            while (Math.abs(error) > 4.0 && guard++ < 15) {
                // A hand does not aim at a fixed rate, and a good share of swings go past.
                double turn = error * (0.4 + random.nextDouble() * 1.0);
                error -= turn;
                deltas.add(turn);
                errors.add(quantise(error));
            }
            for (int tick = 0; tick < 12; tick++) {
                double turn = (random.nextDouble() * 2.0 - 1.0) * 8.0;
                double targetMoved = (random.nextDouble() * 2.0 - 1.0) * 9.0;
                error = error - turn + targetMoved;
                deltas.add(turn);
                errors.add(quantise(error));
            }
        }
        return new Fight(deltas, errors);
    }

    /**
     * Somebody with aim assist running.
     *
     * <p>Every implementation has the same two settings: a cone it works inside and a cap on how
     * fast it may turn. So it closes at that cap, stops dead on the target instead of passing it,
     * and never loses the target once it has it.
     */
    private static Fight byAssist(int engagements, long seed, double capDegreesPerTick) {
        Random random = new Random(seed);
        List<Double> deltas = new ArrayList<Double>();
        List<Double> errors = new ArrayList<Double>();

        for (int engagement = 0; engagement < engagements; engagement++) {
            double error = (random.nextBoolean() ? 1 : -1) * (25.0 + random.nextDouble() * 35.0);
            while (Math.abs(error) > 0.5) {
                double turn = Math.signum(error) * Math.min(capDegreesPerTick, Math.abs(error));
                error -= turn;
                deltas.add(turn);
                errors.add(quantise(error));
            }
            for (int tick = 0; tick < 12; tick++) {
                // The target moves; the assist takes all of it back the same tick.
                double drifted = error + (random.nextDouble() * 2.0 - 1.0) * 6.0;
                double turn = Math.signum(drifted) * Math.min(capDegreesPerTick, Math.abs(drifted));
                error = drifted - turn;
                deltas.add(turn);
                errors.add(quantise(error));
            }
        }
        return new Fight(deltas, errors);
    }

    private static AimAnalysis.Result analyse(Fight fight) {
        return AimAnalysis.analyse(fight.deltas, fight.errors, CONE);
    }

    // -- the failure that matters -------------------------------------------------------------

    @Test
    void aimingByHandIsNotFlagged() {
        for (long seed = 1; seed <= 8; seed++) {
            AimAnalysis.Result result = analyse(byHand(8, seed));
            assertFalse(result.isSuspicious(),
                    "seed " + seed + ": overshoot " + result.getOvershootRate()
                            + ", turn variation " + result.getTurnVariation()
                            + ", tracking " + result.getTrackingRate());
        }
    }

    @Test
    void aCalmPlayerWhoRarelyOvershootsIsStillNotFlaggedOnThatAlone() {
        // One mark is never enough. Somebody on low sensitivity who eases onto targets without
        // passing them still turns at a human range of speeds and still loses ground tracking.
        Random random = new Random(11);
        List<Double> deltas = new ArrayList<Double>();
        List<Double> errors = new ArrayList<Double>();
        for (int engagement = 0; engagement < 8; engagement++) {
            double error = (random.nextBoolean() ? 1 : -1) * (30.0 + random.nextDouble() * 25.0);
            while (Math.abs(error) > 3.0) {
                double turn = error * (0.25 + random.nextDouble() * 0.6); // never past the target
                error -= turn;
                deltas.add(turn);
                errors.add(quantise(error));
            }
            for (int tick = 0; tick < 12; tick++) {
                double turn = (random.nextDouble() * 2.0 - 1.0) * 8.0;
                error = error - turn + (random.nextDouble() * 2.0 - 1.0) * 10.0;
                deltas.add(turn);
                errors.add(quantise(error));
            }
        }
        AimAnalysis.Result result = AimAnalysis.analyse(
                new Fight(deltas, errors).deltas, new Fight(deltas, errors).errors, CONE);
        assertFalse(result.isSuspicious(),
                "one mark alone must not convict; overshoot " + result.getOvershootRate()
                        + ", turn variation " + result.getTurnVariation()
                        + ", tracking " + result.getTrackingRate());
    }

    // -- what it should catch -----------------------------------------------------------------

    @Test
    void rateCappedAssistedAimIsFlagged() {
        AimAnalysis.Result result = analyse(byAssist(8, 5, 8.0));
        assertTrue(result.isSuspicious(),
                "overshoot " + result.getOvershootRate()
                        + ", turn variation " + result.getTurnVariation()
                        + ", tracking " + result.getTrackingRate());
    }

    @Test
    void assistedAimIsFlaggedAtSeveralTurnCaps() {
        for (double cap : new double[]{6.0, 8.0, 12.0, 20.0}) {
            assertTrue(analyse(byAssist(8, 7, cap)).isSuspicious(), "cap " + cap);
        }
    }

    @Test
    void theMarksItReportsAreTheOnesItSaw() {
        AimAnalysis.Result assisted = analyse(byAssist(8, 7, 8.0));
        AimAnalysis.Result human = analyse(byHand(8, 1));

        assertTrue(assisted.getOvershootRate() < human.getOvershootRate(),
                "a machine should pass the target less often than a hand does");
        assertTrue(assisted.getTurnVariation() >= 0.0 && human.getTurnVariation() >= 0.0,
                "both fixtures should turn enough to be measured at all");
        assertTrue(assisted.getTurnVariation() < human.getTurnVariation(),
                "a capped turn rate should vary less than a wrist");
        assertTrue(assisted.getTrackingRate() > human.getTrackingRate(),
                "a machine should lose the target less often");
    }

    @Test
    void tooLittleTurningIsReportedAsUnmeasuredRatherThanAsPerfectlySteady() {
        // A missing measurement must never read as zero variation, which is the single most
        // suspicious value there is. A quiet fight would otherwise convict on nothing at all.
        double[] deltas = new double[120];
        double[] errors = new double[120];
        for (int i = 0; i < errors.length; i++) {
            errors[i] = 2.0; // sitting on target, barely moving
        }
        AimAnalysis.Result result = AimAnalysis.analyse(deltas, errors, CONE);
        assertTrue(result.getTurnVariation() < 0.0, "unmeasured, not zero");
        assertFalse(result.isSuspicious());
    }

    // -- edges --------------------------------------------------------------------------------

    @Test
    void aShortFightSaysNothing() {
        Fight brief = byAssist(1, 3, 8.0);
        assertFalse(AimAnalysis.analyse(brief.deltas, brief.errors, CONE).isSuspicious(),
                "a few seconds of one fight is not a pattern");
    }

    @Test
    void standingStillFacingNobodyIsNotFlagged() {
        double[] deltas = new double[120];
        double[] errors = new double[120];
        for (int i = 0; i < errors.length; i++) {
            errors[i] = 180.0; // no target anywhere
        }
        assertFalse(AimAnalysis.analyse(deltas, errors, CONE).isSuspicious());
    }

    @Test
    void malformedInputIsSafe() {
        assertFalse(AimAnalysis.analyse(null, null, CONE).isSuspicious());
        assertFalse(AimAnalysis.analyse(new double[50], null, CONE).isSuspicious());
        assertFalse(AimAnalysis.analyse(new double[50], new double[10], CONE).isSuspicious(),
                "mismatched lengths must not be read past the end of either");
    }
}
