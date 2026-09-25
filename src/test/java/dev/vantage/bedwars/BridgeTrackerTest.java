package dev.vantage.bedwars;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeTrackerTest {

    private static final double BED_X = 0.0;
    private static final double BED_Z = 0.0;
    private static final double ISLAND = 12.0;
    /** Far enough that every bridge in these tests is close enough to warn about. */
    private static final double WARN_ANYWHERE = 100.0;

    /** Places blocks in a line from a start point along a direction, one every {@code gap} ms. */
    private static long bridge(BridgeTracker tracker, String who, double x, double z, double stepX, double stepZ,
                               int blocks, long start, long gap) {
        long time = start;
        for (int i = 0; i < blocks; i++) {
            tracker.record(who, x + stepX * i, 64, z + stepZ * i, time);
            time += gap;
        }
        return time - gap;
    }

    @Test
    void aBridgeStraightAtYourBedIsARushWithAnArrivalTime() {
        BridgeTracker tracker = new BridgeTracker();
        // From 50 blocks north, a block every 400ms heading south: 2.5 blocks a second.
        long last = bridge(tracker, "Rusher", 0, -50, 0, 1, 12, 10_000, 400);
        List<BridgeTracker.Rush> rushes = tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, last + 100);
        assertEquals(1, rushes.size());
        BridgeTracker.Rush rush = rushes.get(0);
        assertEquals("Rusher", rush.player);
        assertEquals("north", rush.direction());
        assertEquals(2.5, rush.speed, 0.1);
        // Head at z = -39: 39 blocks out, 27 to the island edge, at 2.5 a second.
        assertEquals(27.0 / 2.5, rush.etaSeconds, 0.5);
    }

    @Test
    void aDiagonalBridgeAtYourBedIsARush() {
        BridgeTracker tracker = new BridgeTracker();
        // Diagonal bridging alternates a step east and a step south.
        long time = 0;
        double x = -35;
        double z = -35;
        for (int i = 0; i < 20; i++) {
            tracker.record("Diagonal", x, 64, z, time);
            if (i % 2 == 0) {
                x += 1;
            } else {
                z += 1;
            }
            time += 300;
        }
        assertEquals(1, tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, time).size());
    }

    @Test
    void aBridgeAcrossYourIslandsPathIsNotARush() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Passerby", -20, -30, 1, 0, 14, 0, 350);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, last + 100).isEmpty());
    }

    @Test
    void aBridgeToTheIslandNextDoorIsNotARush() {
        BridgeTracker tracker = new BridgeTracker();
        // Heading south 30 blocks to the west of your bed. It points roughly your way and gets a
        // little closer, but carried on it misses your island by a long way.
        long last = bridge(tracker, "Neighbour", -30, -50, 0, 1, 14, 0, 350);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, last + 100).isEmpty());
    }

    @Test
    void aBridgeGoingAwayIsNotARush() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Leaver", 0, -15, 0, -1, 12, 0, 350);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, last + 100).isEmpty());
    }

    @Test
    void aFewQuickBlocksYourWayAreNotARushYet() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Twitchy", 0, -40, 0, 1, 6, 0, 250);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, last + 100).isEmpty());
    }

    @Test
    void oldBlocksElsewhereDoNotJoinANewShortBridge() {
        BridgeTracker tracker = new BridgeTracker();
        // A long bridge somewhere else entirely, then a few blocks your way from a new spot. On
        // their own the new blocks are too few; they must not borrow the old ones to make up length.
        long last = bridge(tracker, "Builder", 60, 60, 1, 0, 14, 0, 300);
        last = bridge(tracker, "Builder", 0, -45, 0, 1, 5, last + 2000, 300);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, last + 100).isEmpty());
    }

    @Test
    void aRushIsOnlyReportedOnceItIsWithinTheWarnDistance() {
        BridgeTracker tracker = new BridgeTracker();
        // Head ends 49 blocks out, 37 from the island's edge.
        long last = bridge(tracker, "Rusher", 0, -60, 0, 1, 12, 0, 400);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, 25.0, last + 100).isEmpty());
        assertEquals(1, tracker.rushes(BED_X, BED_Z, ISLAND, 40.0, last + 100).size());
    }

    @Test
    void buildingADefenceInOnePlaceIsNotABridge() {
        BridgeTracker tracker = new BridgeTracker();
        long time = 0;
        // Wool piled around a bed 60 blocks away: lots of placements, no direction.
        int[][] around = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {2, 0}};
        for (int[] offset : around) {
            tracker.record("Defender", 60 + offset[0], 64, 60 + offset[1], time);
            time += 300;
        }
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, time).isEmpty());
    }

    @Test
    void anEnemyBuildingOnYourIslandIsNotABridgeToIt() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Inside", 0, -11, 0, 1, 10, 0, 300);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE, last + 100).isEmpty());
    }

    @Test
    void aBridgeThatStoppedIsForgotten() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Quitter", 0, -50, 0, 1, 12, 0, 400);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, WARN_ANYWHERE,
                last + BridgeTracker.STALE_MILLIS + 500).isEmpty());
    }
}
