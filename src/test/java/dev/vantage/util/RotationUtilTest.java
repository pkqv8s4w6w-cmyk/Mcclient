package dev.vantage.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationUtilTest {

    private static final float EPSILON = 1.0e-3f;

    @Test
    void wrapsIntoTheHalfOpenRange() {
        assertEquals(0.0f, RotationUtil.wrap(360.0f), EPSILON);
        assertEquals(-180.0f, RotationUtil.wrap(180.0f), EPSILON);
        assertEquals(170.0f, RotationUtil.wrap(-190.0f), EPSILON);
        assertEquals(10.0f, RotationUtil.wrap(730.0f), EPSILON);
    }

    @Test
    void differenceTakesTheShortWayRound() {
        assertEquals(20.0f, RotationUtil.yawDifference(170.0f, -170.0f), EPSILON);
        assertEquals(-20.0f, RotationUtil.yawDifference(-170.0f, 170.0f), EPSILON);
    }

    @Test
    void rotationsFollowMinecraftsCompass() {
        // +Z is south, which Minecraft calls yaw 0; +X is west-facing yaw -90.
        assertEquals(0.0f, RotationUtil.rotationsFor(0, 0, 1)[0], EPSILON);
        assertEquals(-90.0f, RotationUtil.rotationsFor(1, 0, 0)[0], EPSILON);
        assertEquals(90.0f, RotationUtil.rotationsFor(-1, 0, 0)[0], EPSILON);
        // Looking down is positive pitch.
        assertEquals(45.0f, RotationUtil.rotationsFor(0, -1, 1)[1], EPSILON);
    }

    @Test
    void stepIsCappedPerAxisAndStaysNearTheUnwrappedYaw() {
        float[] step = RotationUtil.stepTowards(720.0f, 0.0f, 90.0f, 60.0f, 30.0f, 10.0f);
        // 720 is facing 0; a 90 degree target is 90 away, so one step is 30 - and stays near 720.
        assertEquals(750.0f, step[0], EPSILON);
        assertEquals(10.0f, step[1], EPSILON);
    }

    @Test
    void stepArrivesExactlyWhenCloserThanTheCap() {
        float[] step = RotationUtil.stepTowards(0.0f, 0.0f, 5.0f, -3.0f, 30.0f, 30.0f);
        assertEquals(5.0f, step[0], EPSILON);
        assertEquals(-3.0f, step[1], EPSILON);
    }

    @Test
    void mouseSnappingProducesWholeSteps() {
        float sensitivity = 0.5f;
        float step = RotationUtil.mouseStep(sensitivity);
        float snapped = RotationUtil.snapToMouse(10.0f, 17.3f, sensitivity);
        float steps = (snapped - 10.0f) / step;
        assertEquals(Math.round(steps), steps, 1.0e-2f);
        assertTrue(Math.abs(snapped - 17.3f) <= step / 2.0f + EPSILON);
    }
}
