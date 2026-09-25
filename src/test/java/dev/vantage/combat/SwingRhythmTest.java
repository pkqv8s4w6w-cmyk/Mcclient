package dev.vantage.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingRhythmTest {

    private static SwingRhythm clicking(long start, long gap, int swings) {
        SwingRhythm rhythm = new SwingRhythm();
        for (int i = 0; i < swings; i++) {
            rhythm.swing(start + i * gap);
        }
        return rhythm;
    }

    @Test
    void aSteadyClickerHasTheirGapAsTheInterval() {
        SwingRhythm rhythm = clicking(1000, 100, 6);
        assertEquals(100, rhythm.interval());
        assertEquals(1600, rhythm.nextSwing(1550));
    }

    @Test
    void oneSwingIsNotARhythm() {
        SwingRhythm rhythm = clicking(1000, 100, 1);
        assertEquals(-1, rhythm.interval());
        assertEquals(-1, rhythm.nextSwing(1000));
    }

    @Test
    void aDoubleClickDoesNotMoveTheInterval() {
        SwingRhythm rhythm = new SwingRhythm();
        long[] swings = {1000, 1125, 1250, 1270, 1395, 1520};
        for (long swing : swings) {
            rhythm.swing(swing);
        }
        assertEquals(125, rhythm.interval());
    }

    @Test
    void aPauseStartsANewRhythm() {
        SwingRhythm rhythm = clicking(1000, 100, 5);
        // A second's pause, then a single swing: the old rhythm no longer applies.
        rhythm.swing(2500);
        assertEquals(-1, rhythm.interval());
    }

    @Test
    void theNextSwingSkipsAheadPastSwingsThatWouldLandTooEarly() {
        SwingRhythm rhythm = clicking(1000, 150, 4);
        // Last swing at 1450. You are immune until 1560, so the swing at 1600 is the one that hurts.
        assertEquals(1600, rhythm.nextSwing(1560));
    }

    @Test
    void somebodyWhoStoppedClickingIsNotPredicted() {
        SwingRhythm rhythm = clicking(1000, 100, 5);
        // Last swing at 1400; asking about 1800 is four gaps later.
        assertEquals(-1, rhythm.nextSwing(1800));
    }

    @Test
    void theBlockStartsARoundTripBeforeTheSwingIsSeen() {
        long seenAt = 2000;
        long ping = 80;
        long interval = 100;
        // The server takes the swing at 1960 by this clock; a block sent at 1920 arrives just then.
        assertTrue(SwingRhythm.inBlockWindow(1920, seenAt, ping, interval));
        assertTrue(SwingRhythm.inBlockWindow(1870, seenAt, ping, interval));
        assertFalse(SwingRhythm.inBlockWindow(1800, seenAt, ping, interval));
        assertFalse(SwingRhythm.inBlockWindow(2000, seenAt, ping, interval));
    }

    @Test
    void slowerClickersGetAWiderWindow() {
        assertEquals(60, SwingRhythm.slack(100));
        assertEquals(105, SwingRhythm.slack(300));
    }
}
