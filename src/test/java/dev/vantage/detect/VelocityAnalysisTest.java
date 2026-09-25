package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityAnalysisTest {

    private static final double EXPECTED = 0.5;

    @Test
    void normalKnockbackIsNotFlagged() {
        double[] hits = {0.9, 1.1, 0.8, 1.3, 1.0, 0.95};
        assertFalse(VelocityAnalysis.analyse(hits, EXPECTED).isSuspicious());
    }

    @Test
    void barelyMovingAfterEveryHitIsFlagged() {
        double[] hits = {0.05, 0.02, 0.08, 0.03, 0.06, 0.04};
        VelocityAnalysis.Result result = VelocityAnalysis.analyse(hits, EXPECTED);
        assertTrue(result.isSuspicious());
        assertTrue(result.getConfidence() > 0.8, "confidence was " + result.getConfidence());
    }

    @Test
    void reducedButPresentKnockbackIsFlaggedLessConfidently() {
        double[] hits = {0.35, 0.30, 0.38, 0.33, 0.36, 0.31};
        VelocityAnalysis.Result result = VelocityAnalysis.analyse(hits, EXPECTED);
        assertTrue(result.isSuspicious());
        assertTrue(result.getConfidence() < 0.5, "confidence was " + result.getConfidence());
    }

    @Test
    void oneHitIntoAWallDoesNotConvict() {
        // Being hit against a wall legitimately produces almost no displacement.
        double[] hits = {1.0, 0.9, 0.02, 1.1, 0.95, 1.05};
        assertFalse(VelocityAnalysis.analyse(hits, EXPECTED).isSuspicious());
    }

    @Test
    void tooFewHitsYieldNothing() {
        assertFalse(VelocityAnalysis.analyse(new double[]{0.0, 0.0}, EXPECTED).isSuspicious());
        assertFalse(VelocityAnalysis.analyse(null, EXPECTED).isSuspicious());
    }

    @Test
    void theMedianIsReportedForTheMessage() {
        double[] hits = {0.1, 0.1, 0.1, 0.1};
        assertTrue(Math.abs(VelocityAnalysis.analyse(hits, EXPECTED).getMedianDisplacement() - 0.1) < 1e-9);
    }
}
