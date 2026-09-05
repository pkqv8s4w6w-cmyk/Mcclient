package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachAnalysisTest {

    private static final double ALLOWED = ReachAnalysis.DEFAULT_ALLOWED_REACH;

    /**
     * Hits that were legal against where you were, whatever they measured on arrival.
     *
     * @param arrivedAt how far away they looked when the damage landed
     * @param rewoundTo how far away they were at the closest recent moment
     */
    private static List<HitSample> hits(int count, double arrivedAt, double rewoundTo,
                                        int ticksAgo, long seed) {
        Random random = new Random(seed);
        List<HitSample> samples = new ArrayList<HitSample>();
        for (int i = 0; i < count; i++) {
            double jitter = (random.nextDouble() * 2.0 - 1.0) * 0.15;
            samples.add(new HitSample(arrivedAt + jitter, rewoundTo + jitter, ticksAgo));
        }
        return samples;
    }

    // -- the failure that matters -------------------------------------------------------------

    @Test
    void ordinaryMeleeIsNotFlagged() {
        assertFalse(ReachAnalysis.analyse(hits(12, 2.8, 2.6, 1, 1), ALLOWED).isSuspicious());
    }

    @Test
    void aLaggyHonestPlayerIsNotFlagged() {
        // This is the whole point of rewinding. Their hits arrive looking four metres long,
        // because you have moved since; against where you actually were they are ordinary. The
        // old check measured only the first number and convicted anybody with a connection.
        List<HitSample> laggy = hits(12, 4.2, 2.7, 6, 2);
        assertFalse(ReachAnalysis.analyse(laggy, ALLOWED).isSuspicious(),
                "a hit that was legal a moment ago is not reach");
    }

    @Test
    void oneLongReadingDoesNotConvict() {
        List<HitSample> samples = hits(12, 2.8, 2.7, 1, 3);
        samples.set(5, new HitSample(6.0, 6.0, 0));
        assertFalse(ReachAnalysis.analyse(samples, ALLOWED).isSuspicious(),
                "the median exists so one spike cannot decide");
    }

    // -- what it should catch -----------------------------------------------------------------

    @Test
    void hitsThatWereNeverLegalFromAnywhereAreFlagged() {
        ReachAnalysis.Result result = ReachAnalysis.analyse(hits(12, 4.1, 4.0, 0, 4), ALLOWED);
        assertTrue(result.isSuspicious(), "median was " + result.getMedian());
    }

    @Test
    void theFurtherPastTheAllowanceTheMoreCertain() {
        double modest = ReachAnalysis.analyse(hits(12, 3.7, 3.7, 0, 5), ALLOWED).getConfidence();
        double blatant = ReachAnalysis.analyse(hits(12, 4.5, 4.5, 0, 6), ALLOWED).getConfidence();
        assertTrue(blatant > modest);
        assertEquals(1.0, blatant, 1e-9, "a metre past the allowance is as sure as it gets");
    }

    // -- edges --------------------------------------------------------------------------------

    @Test
    void aHandfulOfHitsSaysNothing() {
        // Was five, which is a couple of seconds of one fight — and catching somebody at the edge
        // of their range twice while you both strafe is not a pattern.
        assertFalse(ReachAnalysis.analyse(hits(5, 5.0, 5.0, 0, 7), ALLOWED).isSuspicious());
    }

    @Test
    void malformedInputIsSafe() {
        assertFalse(ReachAnalysis.analyse((List<HitSample>) null, ALLOWED).isSuspicious());
        assertFalse(ReachAnalysis.analyse((double[]) null, ALLOWED).isSuspicious());
        assertFalse(ReachAnalysis.analyse(new double[0], ALLOWED).isSuspicious());
    }

    @Test
    void theAllowanceSitsAboveVanillaRange() {
        // Even after rewinding, a client's copy of somebody else's position is quantised and a
        // tick behind, so a legitimate hit at the edge of range measures a little long here.
        assertTrue(ALLOWED > ReachAnalysis.VANILLA_REACH);
        assertTrue(ALLOWED < ReachAnalysis.VANILLA_REACH + 1.0, "but not so far as to be useless");
    }
}
