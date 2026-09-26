package dev.vantage.bedwars;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeTrackerTest {

    private static final double BED_X = 0.0;
    private static final double BED_Z = 0.0;
    private static final IslandShape ISLAND = IslandShape.circle(BED_X, BED_Z, 12.0);

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
        List<BridgeTracker.Rush> rushes = tracker.rushes(BED_X, BED_Z, ISLAND, last + 100);
        assertEquals(1, rushes.size());
        BridgeTracker.Rush rush = rushes.get(0);
        assertEquals("Rusher", rush.player);
        assertEquals(2.5, rush.speed, 0.1);
        // Head at z = -39: 39 blocks out, 27 to the island edge, at 2.5 a second.
        assertEquals(27.0 / 2.5, rush.etaSeconds, 0.5);
    }

    @Test
    void theArrivalTimeCountsToTheIslandsRealEdge() {
        // The island runs 25 blocks north of the bed, so the bridge head at z = -39 is 14 short.
        IslandShape longIsland = IslandShape.detect((x, z) -> x >= -5 && x <= 5 && z >= -25 && z <= 5
                ? IslandShape.Column.LAND : IslandShape.Column.OPEN, 0, 0, 40);
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Rusher", 0, -50, 0, 1, 12, 10_000, 400);
        List<BridgeTracker.Rush> rushes = tracker.rushes(BED_X, BED_Z, longIsland, last + 100);
        assertEquals(1, rushes.size());
        assertEquals(14.0 / 2.5, rushes.get(0).etaSeconds, 0.5);
    }

    @Test
    void aBridgeAcrossYourIslandsPathIsNotARush() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Passerby", -20, -30, 1, 0, 14, 0, 350);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, last + 100).isEmpty());
    }

    @Test
    void aBridgeGoingAwayIsNotARush() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Leaver", 0, -15, 0, -1, 12, 0, 350);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, last + 100).isEmpty());
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
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, time).isEmpty());
    }

    @Test
    void aBridgeThatStoppedIsForgotten() {
        BridgeTracker tracker = new BridgeTracker();
        long last = bridge(tracker, "Quitter", 0, -50, 0, 1, 12, 0, 400);
        assertTrue(tracker.rushes(BED_X, BED_Z, ISLAND, last + BridgeTracker.STALE_MILLIS + 500).isEmpty());
    }
}
