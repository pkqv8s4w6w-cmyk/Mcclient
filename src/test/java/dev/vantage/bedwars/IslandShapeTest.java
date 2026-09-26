package dev.vantage.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandShapeTest {

    private static final int LIMIT = 40;

    private static boolean inDisc(int x, int z, int centreX, int centreZ, int radius) {
        int dx = x - centreX;
        int dz = z - centreZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    private static IslandShape.Column land(boolean solid) {
        return solid ? IslandShape.Column.LAND : IslandShape.Column.OPEN;
    }

    @Test
    void aRoundIslandIsFoundWhole() {
        IslandShape island = IslandShape.detect((x, z) -> land(inDisc(x, z, 0, 0, 10)), 0, 0, LIMIT);
        assertNotNull(island);
        int solid = 0;
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                if (inDisc(x, z, 0, 0, 10)) {
                    solid++;
                }
            }
        }
        assertEquals(solid, island.size());
        assertTrue(island.contains(10.5, 0.5));
        assertTrue(island.contains(-9.5, 0.5));
        assertFalse(island.contains(11.5, 0.5));
        assertTrue(island.isComplete());
    }

    @Test
    void aSquareIslandKeepsItsCorners() {
        IslandShape island = IslandShape.detect((x, z) -> land(x >= 0 && x < 10 && z >= 0 && z < 10), 5, 5, LIMIT);
        assertNotNull(island);
        assertEquals(100, island.size());
        assertTrue(island.contains(0.5, 0.5));
        assertTrue(island.contains(9.5, 9.5));
    }

    @Test
    void bridgesOffTheIslandAreLeftOut() {
        // One bridge a block wide heading east, one two blocks wide heading north.
        IslandShape island = IslandShape.detect((x, z) -> land(inDisc(x, z, 0, 0, 10)
                || (z == 0 && x > 0 && x <= 40)
                || ((x == 0 || x == 1) && z < 0 && z >= -35)), 0, 0, LIMIT);
        assertNotNull(island);
        assertFalse(island.contains(20.5, 0.5));
        assertFalse(island.contains(0.5, -20.5));
        // Only the first block of each bridge is taken in with the rim.
        assertFalse(island.contains(12.5, 0.5));
        assertFalse(island.contains(0.5, -12.5));
        assertEquals(19.0, island.distanceTo(30.5, 0.5), 1.0);
    }

    @Test
    void aWidePathOutToTheGeneratorIsPartOfTheIsland() {
        IslandShape island = IslandShape.detect((x, z) -> land(inDisc(x, z, 0, 0, 8)
                || (x >= 8 && x <= 20 && Math.abs(z) <= 1)
                || (x >= 20 && x <= 24 && Math.abs(z) <= 2)), 0, 0, LIMIT);
        assertNotNull(island);
        assertTrue(island.contains(22.5, 0.5));
        assertTrue(island.contains(24.5, 2.5));
    }

    @Test
    void aNeighbouringIslandIsNotPartOfYours() {
        IslandShape island = IslandShape.detect((x, z) -> land(inDisc(x, z, 0, 0, 8) || inDisc(x, z, 20, 0, 6)),
                0, 0, LIMIT);
        assertNotNull(island);
        assertFalse(island.contains(20.5, 0.5));
        assertEquals(6.0, island.distanceTo(14.5, 0.5), 1.0);
    }

    @Test
    void unloadedGroundLeavesItUnfinished() {
        IslandShape island = IslandShape.detect((x, z) -> x > 5 ? IslandShape.Column.UNLOADED
                : land(inDisc(x, z, 0, 0, 10)), 0, 0, LIMIT);
        assertNotNull(island);
        assertFalse(island.isComplete());
        assertFalse(island.contains(8.5, 0.5));
    }

    @Test
    void noGroundMeansNoIsland() {
        assertNull(IslandShape.detect((x, z) -> IslandShape.Column.OPEN, 0, 0, LIMIT));
    }

    @Test
    void aBedOnTheRimStillFindsTheIsland() {
        // Standing on the island's edge, where the start column itself fails the four-sides test.
        IslandShape island = IslandShape.detect((x, z) -> land(inDisc(x, z, 0, 0, 10)), 10, 0, LIMIT);
        assertNotNull(island);
        assertTrue(island.contains(0.5, 0.5));
    }

    @Test
    void theWalkStopsAtTheLimit() {
        IslandShape island = IslandShape.detect((x, z) -> IslandShape.Column.LAND, 0, 0, 10);
        assertNotNull(island);
        assertEquals(21 * 21, island.size());
    }

    @Test
    void distanceIsMeasuredToTheNearestEdge() {
        IslandShape island = IslandShape.detect((x, z) -> land(x >= 0 && x < 10 && z >= 0 && z < 10), 5, 5, LIMIT);
        assertNotNull(island);
        assertEquals(0.0, island.distanceTo(5.0, 5.0), 1.0e-9);
        assertEquals(5.0, island.distanceTo(15.0, 5.0), 1.0e-9);
        assertEquals(5.0, island.distanceTo(13.0, 14.0), 1.0e-9);
    }

    @Test
    void aSingleColumnIsOutlinedOnAllFourSides() {
        IslandShape column = IslandShape.circle(0.5, 0.5, 0.5);
        assertEquals(1, column.size());
        assertEquals(4, column.outline().size());
    }
}
