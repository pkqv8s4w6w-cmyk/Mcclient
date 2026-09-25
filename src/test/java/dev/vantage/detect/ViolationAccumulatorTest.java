package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationAccumulatorTest {

    private static final double HALF_LIFE = 30_000.0;
    /** Matches the detector, so two full-confidence verdicts clear the bar with room for decay. */
    private static final double NOTIFY_AT = 4.5;

    private static ViolationAccumulator fresh() {
        return new ViolationAccumulator(HALF_LIFE, NOTIFY_AT);
    }

    // -- the failure that matters -------------------------------------------------------------

    @Test
    void oneCheckSpeakingOnceNeverConvictsHoweverSureItIs() {
        // Every check here is a heuristic reading indirect data, and each has some rate of being
        // wrong about somebody honest. One of them being very confident once is exactly the case
        // that used to flag half a lobby.
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 100.0, 0L);

        assertTrue(accumulator.get(0L) > NOTIFY_AT, "the score is well past the bar");
        assertFalse(accumulator.isOverThreshold(0L), "but nothing corroborates it");
        assertFalse(accumulator.shouldNotify(0L));
    }

    @Test
    void oneCheckSpeakingTwiceStillDoesNot() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 3.0, 0L);
        accumulator.add("Reach", 3.0, 1_000L);
        assertFalse(accumulator.isOverThreshold(1_000L));
    }

    // -- what does convict --------------------------------------------------------------------

    @Test
    void oneCheckHoldingUpAcrossSeveralWindowsConvicts() {
        // By the third separate window it is describing behaviour that persisted, not one stretch
        // of play that happened to look odd.
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 2.5, 0L);
        accumulator.add("Reach", 2.5, 1_000L);
        accumulator.add("Reach", 2.5, 2_000L);
        assertTrue(accumulator.isOverThreshold(2_000L));
    }

    @Test
    void twoDifferentChecksAgreeingConvicts() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 2.5, 0L);
        accumulator.add("Autoclicker", 2.5, 1_000L);
        assertTrue(accumulator.isOverThreshold(1_000L));
    }

    @Test
    void agreementStillHasToClearTheBar() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 0.8, 0L);
        accumulator.add("Autoclicker", 0.8, 1_000L);
        assertFalse(accumulator.isOverThreshold(1_000L), "two weak signals are still weak");
    }

    // -- fading -------------------------------------------------------------------------------

    @Test
    void suspicionHalvesOverTheHalfLife() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 8.0, 0L);
        assertEquals(4.0, accumulator.get((long) HALF_LIFE), 1e-6);
        assertEquals(2.0, accumulator.get((long) HALF_LIFE * 2), 1e-6);
    }

    @Test
    void aPlayerWhoTripsACheckOnceAndThenPlaysNormallyGoesBackToClean() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 2.5, 0L);
        assertEquals(0.0, accumulator.get(600_000L), 1e-9);
        assertFalse(accumulator.isOverThreshold(600_000L));
    }

    @Test
    void aFadedCheckStopsCountingTowardCorroboration() {
        // Otherwise an old sighting could combine with a new one to convict on evidence that no
        // longer exists.
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 2.5, 0L);
        accumulator.add("Reach", 2.5, 1_000L);

        long muchLater = 600_000L;
        accumulator.add("Reach", 2.5, muchLater);
        assertFalse(accumulator.isOverThreshold(muchLater),
                "the two old windows have faded, so this is a first sighting again");
    }

    // -- notification -------------------------------------------------------------------------

    @Test
    void itOnlySpeaksUpOnce() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 2.5, 0L);
        accumulator.add("Autoclicker", 2.5, 1_000L);

        assertTrue(accumulator.shouldNotify(1_000L));
        assertFalse(accumulator.shouldNotify(1_000L), "one cheater must not fill the chat");
        accumulator.add("Reach", 5.0, 2_000L);
        assertFalse(accumulator.shouldNotify(2_000L));
    }

    @Test
    void itCanSpeakAgainOnceEverythingHasFaded() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 2.5, 0L);
        accumulator.add("Autoclicker", 2.5, 1_000L);
        assertTrue(accumulator.shouldNotify(1_000L));

        long muchLater = 900_000L;
        accumulator.get(muchLater);
        accumulator.add("Reach", 2.5, muchLater);
        accumulator.add("Autoclicker", 2.5, muchLater + 1_000L);
        assertTrue(accumulator.shouldNotify(muchLater + 1_000L));
    }

    // -- reporting ----------------------------------------------------------------------------

    @Test
    void itNamesWhicheverCheckHasTheMostToSay() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 1.0, 0L);
        accumulator.add("Autoclicker", 4.0, 0L);
        assertEquals("Autoclicker", accumulator.strongestCheck(0L));
    }

    @Test
    void itCountsHowManyChecksAreLive() {
        ViolationAccumulator accumulator = fresh();
        assertEquals(0, accumulator.liveChecks(0L));
        accumulator.add("Reach", 2.0, 0L);
        assertEquals(1, accumulator.liveChecks(0L));
        accumulator.add("Aim assist", 2.0, 0L);
        assertEquals(2, accumulator.liveChecks(0L));
    }

    @Test
    void negativeAndZeroWeightsAreIgnored() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", -5.0, 0L);
        accumulator.add("Reach", 0.0, 0L);
        assertEquals(0.0, accumulator.get(0L), 1e-9);
        assertEquals(0, accumulator.liveChecks(0L));
    }

    @Test
    void resettingClearsEverything() {
        ViolationAccumulator accumulator = fresh();
        accumulator.add("Reach", 2.5, 0L);
        accumulator.add("Autoclicker", 2.5, 0L);
        accumulator.reset();
        assertEquals(0.0, accumulator.get(0L), 1e-9);
        assertFalse(accumulator.isOverThreshold(0L));
    }
}
