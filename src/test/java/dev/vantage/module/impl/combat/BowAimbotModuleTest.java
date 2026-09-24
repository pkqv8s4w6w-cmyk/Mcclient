package dev.vantage.module.impl.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BowAimbotModuleTest {

    /** Flies an arrow with vanilla physics and reports its height when it reaches the distance. */
    private static double heightAtDistance(float pitch, double distance, double velocity) {
        double radians = Math.toRadians(pitch);
        double vx = Math.cos(radians) * velocity;
        double vy = -Math.sin(radians) * velocity;
        double x = 0.0;
        double y = 0.0;
        while (x + vx < distance) {
            x += vx;
            y += vy;
            vx *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return y + vy * (distance - x) / vx;
    }

    @Test
    void closeLevelTargetNeedsNearlyFlatAim() {
        Float pitch = BowAimbotModule.solvePitch(10.0, 0.0, 3.0);
        assertNotNull(pitch);
        assertTrue(pitch < 0.0f && pitch > -5.0f, "pitch was " + pitch);
    }

    @Test
    void theSolvedPitchActuallyHitsAtRange() {
        for (double distance : new double[]{15.0, 30.0, 50.0}) {
            Float pitch = BowAimbotModule.solvePitch(distance, 2.0, 3.0);
            assertNotNull(pitch, "no solution at " + distance);
            double arrival = heightAtDistance(pitch, distance, 3.0);
            assertTrue(Math.abs(arrival - 2.0) < 0.1, "arrived at " + arrival + " for " + distance);
        }
    }

    @Test
    void furtherTargetsNeedHigherAim() {
        assertTrue(BowAimbotModule.solvePitch(40.0, 0.0, 3.0) < BowAimbotModule.solvePitch(20.0, 0.0, 3.0));
    }

    @Test
    void aWeakDrawCannotReachAFarTarget() {
        assertNull(BowAimbotModule.solvePitch(120.0, 0.0, 0.8));
    }
}
