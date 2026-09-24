package dev.vantage.bedwars;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreachPathTest {

    private static final int WOOL = 10;
    private static final int END_STONE = 60;
    private static final int OBSIDIAN = 900;

    /**
     * A 9 x 4 x 9 box with a bed in the middle of the bottom layer, wrapped in a shell of the given
     * block two deep on every side and on top.
     */
    private static BreachPath.ArrayGrid shelled(int shellCost) {
        BreachPath.ArrayGrid grid = new BreachPath.ArrayGrid(9, 4, 9);
        for (int x = 2; x <= 6; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 2; z <= 6; z++) {
                    grid.set(x, y, z, shellCost);
                }
            }
        }
        grid.set(4, 0, 4, 0);
        grid.set(4, 0, 5, 0);
        grid.setBed(4, 0, 4);
        grid.setBed(4, 0, 5);
        // Clear the cells directly beside and above the bed inside the shell, as a real defence
        // is built around the bed rather than through it.
        return grid;
    }

    @Test
    void anOpenBedCostsNothing() {
        BreachPath.ArrayGrid grid = new BreachPath.ArrayGrid(5, 3, 5);
        grid.setBed(2, 0, 2);
        BreachPath.Plan plan = BreachPath.plan(grid);
        assertNotNull(plan);
        assertEquals(0, plan.totalTicks);
        assertTrue(plan.blocksToBreak().isEmpty());
    }

    @Test
    void goesThroughTheThinnestPartOfTheShell() {
        BreachPath.ArrayGrid grid = shelled(WOOL);
        // Thicken everything except the north face with obsidian.
        for (int x = 1; x <= 7; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = 1; z <= 7; z++) {
                    if (!grid.isBed(x, y, z) && grid.cost(x, y, z) == 0 && z != 1 && z != 0) {
                        boolean inside = x >= 2 && x <= 6 && z >= 2 && z <= 6 && y <= 2;
                        if (!inside) {
                            grid.set(x, y, z, OBSIDIAN);
                        }
                    }
                }
            }
        }
        BreachPath.Plan plan = BreachPath.plan(grid);
        assertNotNull(plan);
        // Two layers of wool from the north (z = 2 and 3) is all it takes.
        assertEquals(2 * WOOL, plan.totalTicks);
        for (BreachPath.Step step : plan.blocksToBreak()) {
            assertEquals(WOOL, step.cost);
        }
    }

    @Test
    void prefersSeveralSoftBlocksOverOneHardOne() {
        BreachPath.ArrayGrid grid = new BreachPath.ArrayGrid(7, 2, 3);
        grid.setBed(3, 0, 1);
        // Everything is end stone except a longer route of wool.
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 3; z++) {
                    if (!grid.isBed(x, y, z)) {
                        grid.set(x, y, z, END_STONE);
                    }
                }
            }
        }
        for (int x = 0; x <= 2; x++) {
            grid.set(x, 0, 1, WOOL);
        }
        BreachPath.Plan plan = BreachPath.plan(grid);
        assertNotNull(plan);
        assertEquals(3 * WOOL, plan.totalTicks);
    }

    @Test
    void aBedSealedInBedrockHasNoPlan() {
        BreachPath.ArrayGrid grid = new BreachPath.ArrayGrid(3, 2, 3);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 3; z++) {
                    grid.set(x, y, z, BreachPath.UNBREAKABLE);
                }
            }
        }
        grid.set(1, 0, 1, 0);
        grid.setBed(1, 0, 1);
        assertNull(BreachPath.plan(grid));
    }

    @Test
    void breakTimesFollowVanillasFormula() {
        // Wool: hardness 0.8, by hand (speed 1, harvestable) -> 1 / (1 / 0.8 / 30) = 24 ticks.
        assertEquals(24, BreachPath.breakTicks(0.8f, 1.0f, true));
        // With shears (speed 5 on wool) it is 5 ticks.
        assertEquals(5, BreachPath.breakTicks(0.8f, 5.0f, true));
        // End stone (3.0) by hand cannot be harvested: 1 / (1 / 3 / 100) = 300 ticks.
        assertEquals(300, BreachPath.breakTicks(3.0f, 1.0f, false));
        assertEquals(BreachPath.UNBREAKABLE, BreachPath.breakTicks(-1.0f, 1.0f, true));
    }
}
