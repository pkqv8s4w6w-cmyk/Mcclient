package dev.vantage.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticsTest {

    @Test
    void aPearlSolutionLandsWhereItWasAimed() {
        for (double distance : new double[]{8.0, 20.0, 35.0}) {
            Float pitch = Ballistics.solvePitch(Simulation.Kind.THROWABLE, distance, -3.0, 1.5);
            assertNotNull(pitch, "no pearl solution at " + distance);
            Double error = Ballistics.heightErrorAt(Simulation.Kind.THROWABLE, pitch, distance, -3.0, 1.5);
            assertNotNull(error);
            assertTrue(Math.abs(error) < 0.1, "missed by " + error + " at " + distance);
        }
    }

    @Test
    void aPearlCannotReachAHundredBlocks() {
        assertNull(Ballistics.solvePitch(Simulation.Kind.THROWABLE, 100.0, 0.0, 1.5));
    }
}
