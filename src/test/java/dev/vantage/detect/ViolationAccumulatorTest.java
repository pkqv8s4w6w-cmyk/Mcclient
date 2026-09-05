package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationAccumulatorTest {

    @Test
    void scoreBuildsUpAcrossReadings() {
        ViolationAccumulator accumulator = new ViolationAccumulator(30_000.0, 5.0);
        accumulator.add(2.0, 1000L);
        accumulator.add(2.0, 1100L);
        assertTrue(accumulator.get(1100L) > 3.9);
    }

    @Test
    void scoreHalvesOverTheHalfLife() {
        ViolationAccumulator accumulator = new ViolationAccumulator(10_000.0, 100.0);
        accumulator.add(8.0, 0L);
        accumulator.get(1L); // establish the baseline timestamp
        assertEquals(4.0, accumulator.get(10_001L), 0.05);
    }

    @Test
    void oneOddReadingFadesBackToClean() {
        ViolationAccumulator accumulator = new ViolationAccumulator(5_000.0, 6.0);
        accumulator.add(3.0, 1000L);
        assertFalse(accumulator.isOverThreshold(1000L));
        // A player who trips a check once and then plays normally must not carry it all game.
        assertEquals(0.0, accumulator.get(1000L + 5_000L * 12), 1e-6);
    }

    @Test
    void notifiesOnceRatherThanEveryTick() {
        ViolationAccumulator accumulator = new ViolationAccumulator(60_000.0, 5.0);
        accumulator.add(6.0, 1000L);

        assertTrue(accumulator.shouldNotify(1000L));
        assertFalse(accumulator.shouldNotify(1001L), "must not repeat while still over threshold");
        assertFalse(accumulator.shouldNotify(2000L));
    }

    @Test
    void aPlayerCanBeReportedAgainAfterTheyHaveGoneClean() {
        ViolationAccumulator accumulator = new ViolationAccumulator(1_000.0, 5.0);
        accumulator.add(6.0, 1000L);
        assertTrue(accumulator.shouldNotify(1000L));

        // Long enough for the score to fade to nothing.
        long muchLater = 1000L + 1_000L * 20;
        assertEquals(0.0, accumulator.get(muchLater), 1e-6);

        accumulator.add(6.0, muchLater);
        assertTrue(accumulator.shouldNotify(muchLater), "a fresh run of violations should report again");
    }

    @Test
    void resetClearsEverything() {
        ViolationAccumulator accumulator = new ViolationAccumulator(60_000.0, 5.0);
        accumulator.add(9.0, 1000L);
        accumulator.shouldNotify(1000L);
        accumulator.reset();

        assertEquals(0.0, accumulator.get(2000L), 1e-9);
        accumulator.add(9.0, 2000L);
        assertTrue(accumulator.shouldNotify(2000L));
    }
}
