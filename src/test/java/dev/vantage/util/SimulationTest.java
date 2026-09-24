package dev.vantage.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationTest {

    /** A flat floor whose top surface is at the given height. */
    private static Simulation.Collider floorAt(double height) {
        return (x0, y0, z0, x1, y1, z1) -> {
            if (y1 >= height || y0 < height) {
                return -1.0;
            }
            return (y0 - height) / (y0 - y1);
        };
    }

    @Test
    void aPearlThrownLevelLandsOnTheFloorAhead() {
        // Pearls leave at 1.5 blocks a tick.
        Simulation.Path path = Simulation.projectile(Simulation.Kind.THROWABLE,
                0, 10, 0, 0, 0, 1.5, 0, 0, 0, 200, -64, floorAt(0));
        assertNotNull(path.impact);
        assertEquals(0.0, path.impact[1], 1.0e-6);
        assertTrue(path.impact[2] > 10.0, "landed at z=" + path.impact[2]);
        assertFalse(path.fellIntoVoid);
    }

    @Test
    void theFirstTicksMatchVanillaOrderOfDragThenGravity() {
        Simulation.Path path = Simulation.projectile(Simulation.Kind.ARROW,
                0, 100, 0, 0, 1, 0, 0, 0, 0, 3, -64, Simulation.OPEN_AIR);
        // tick 1: y += 1 -> 101, then v = 1 * 0.99 - 0.05 = 0.94
        assertEquals(101.0, path.points.get(1)[1], 1.0e-9);
        // tick 2: y += 0.94 -> 101.94
        assertEquals(101.94, path.points.get(2)[1], 1.0e-9);
    }

    @Test
    void aFireballKeepsAcceleratingTowardItsHeading() {
        Simulation.Path path = Simulation.projectile(Simulation.Kind.FIREBALL,
                0, 50, 0, 0, 0, 0, 0, 0, 0.1, 40, -64, Simulation.OPEN_AIR);
        double[] last = path.points.get(path.points.size() - 1);
        assertEquals(50.0, last[1], 1.0e-9);
        assertTrue(last[2] > 30.0);
    }

    @Test
    void aPlayerPushedOffAnEdgeWithNothingBelowFallsIntoTheVoid() {
        Simulation.Path path = Simulation.fallingPlayer(0, 60, 0, 0.6, 0.4, 0, 400, 0, Simulation.OPEN_AIR);
        assertTrue(path.fellIntoVoid);
        assertNull(path.impact);
    }

    @Test
    void aPlayerOverSolidGroundLandsOnIt() {
        Simulation.Path path = Simulation.fallingPlayer(0, 60, 0, 0.3, 0.0, 0, 400, 0, floorAt(55));
        assertFalse(path.fellIntoVoid);
        assertNotNull(path.impact);
        assertEquals(55.0, path.impact[1], 1.0e-6);
    }

    @Test
    void fullyDrawnBowFiresAtThreeBlocksATick() {
        assertEquals(3.0, Simulation.bowVelocity(20), 1.0e-9);
        assertEquals(3.0, Simulation.bowVelocity(40), 1.0e-9);
        assertTrue(Simulation.bowVelocity(5) < 1.5);
    }
}
