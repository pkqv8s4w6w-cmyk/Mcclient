package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionHistoryTest {

    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.8;

    // -- the geometry the server itself uses ---------------------------------------------------

    @Test
    void aPointInsideABoxIsNoDistanceAway() {
        assertEquals(0.0, PositionHistory.distanceToBox(0.5, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
    }

    @Test
    void distanceIsToTheNearestFaceNotTheCentre() {
        // Measuring centre to centre reads about half a block long for everybody, which would put
        // an entire lobby over any sane allowance.
        assertEquals(2.0, PositionHistory.distanceToBox(3.0, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
    }

    @Test
    void diagonalDistanceIsMeasuredProperly() {
        double expected = Math.sqrt(3.0 * 3.0 + 4.0 * 4.0);
        assertEquals(expected, PositionHistory.distanceToBox(4.0, 5.0, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
    }

    // -- the ring ------------------------------------------------------------------------------

    @Test
    void theMostRecentSampleIsTicksAgoZero() {
        PositionHistory history = new PositionHistory();
        history.record(1.0, 2.0, 3.0);
        history.record(4.0, 5.0, 6.0);

        assertEquals(4.0, history.at(0)[0], 1e-9);
        assertEquals(1.0, history.at(1)[0], 1e-9);
        assertNull(history.at(2), "nothing older than that was recorded");
    }

    @Test
    void olderSamplesFallOffTheEnd() {
        PositionHistory history = new PositionHistory();
        for (int i = 0; i < PositionHistory.CAPACITY * 3; i++) {
            history.record(i, 64.0, 0.0);
        }
        assertEquals(PositionHistory.CAPACITY, history.size());
        assertEquals(PositionHistory.CAPACITY * 3 - 1, history.at(0)[0], 1e-9);
        assertEquals(PositionHistory.CAPACITY * 2, history.at(PositionHistory.CAPACITY - 1)[0], 1e-9);
        assertNull(history.at(PositionHistory.CAPACITY));
    }

    @Test
    void clearingEmptiesIt() {
        PositionHistory history = new PositionHistory();
        history.record(1.0, 2.0, 3.0);
        history.clear();
        assertEquals(0, history.size());
        assertNull(history.closestApproach(0, 0, 0, HALF_WIDTH, HEIGHT, 20));
    }

    // -- the question the server asks -----------------------------------------------------------

    @Test
    void itFindsWhenYouWereClosestAndHowLongAgo() {
        // Walking away from where somebody is standing: the closest you came is the oldest sample
        // on record, and the check has to be able to say so.
        PositionHistory history = new PositionHistory();
        for (int tick = 0; tick < 10; tick++) {
            history.record(tick, 64.0, 0.0);
        }
        PositionHistory.Approach approach =
                history.closestApproach(0.0, 65.0, 0.0, HALF_WIDTH, HEIGHT, 20);

        assertNotNull(approach);
        assertEquals(9, approach.getTicksAgo(), "you were closest nine ticks back");
        assertTrue(approach.getDistance() < 0.5, "and you were standing on the spot");
    }

    @Test
    void standingStillMeansTheClosestApproachIsRightNow() {
        PositionHistory history = new PositionHistory();
        for (int tick = 0; tick < 20; tick++) {
            history.record(0.0, 64.0, 0.0);
        }
        PositionHistory.Approach approach =
                history.closestApproach(3.0, 65.0, 0.0, HALF_WIDTH, HEIGHT, 20);
        assertEquals(0, approach.getTicksAgo());
    }

    @Test
    void itWillNotRewindFurtherThanItIsAskedTo() {
        // The rewind window is what separates lag compensation from hitting where somebody was ten
        // seconds ago, so it has to be a hard bound.
        PositionHistory history = new PositionHistory();
        for (int tick = 0; tick < 30; tick++) {
            history.record(tick, 64.0, 0.0);
        }
        PositionHistory.Approach approach =
                history.closestApproach(0.0, 65.0, 0.0, HALF_WIDTH, HEIGHT, 5);
        assertTrue(approach.getTicksAgo() < 5);
    }

    @Test
    void aHitThatWasNeverLegalStaysFar() {
        // Somebody standing well away the whole time: no amount of rewinding brings them in range.
        PositionHistory history = new PositionHistory();
        for (int tick = 0; tick < 20; tick++) {
            history.record(0.0, 64.0, 0.0);
        }
        PositionHistory.Approach approach =
                history.closestApproach(6.0, 64.9, 0.0, HALF_WIDTH, HEIGHT, 20);
        assertTrue(approach.getDistance() > 5.0, "measured " + approach.getDistance());
    }
}
