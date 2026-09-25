package dev.vantage.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnappingTest {

    private static final float[] NONE = new float[0];
    private static final float THRESHOLD = 4.0f;
    private static final float MARGIN = 3.0f;

    private static Snapping.Result snap(float candidate, float size, float container) {
        return Snapping.snap(candidate, size, container, NONE, NONE, THRESHOLD, MARGIN);
    }

    @Test
    void pullsToTheNearEdgeMargin() {
        Snapping.Result result = snap(4.5f, 50.0f, 400.0f);
        assertTrue(result.snapped);
        assertEquals(MARGIN, result.value, 1e-4);
    }

    @Test
    void pullsToTheFarEdgeMargin() {
        // container 400, size 50 -> far edge target is 347
        Snapping.Result result = snap(345.0f, 50.0f, 400.0f);
        assertTrue(result.snapped);
        assertEquals(347.0f, result.value, 1e-4);
    }

    @Test
    void pullsToCentre() {
        // (400 - 50) / 2 = 175
        Snapping.Result result = snap(176.5f, 50.0f, 400.0f);
        assertTrue(result.snapped);
        assertEquals(175.0f, result.value, 1e-4);
    }

    @Test
    void leavesThePositionAloneBeyondTheThreshold() {
        Snapping.Result result = snap(200.0f, 50.0f, 400.0f);
        assertFalse(result.snapped);
        assertEquals(200.0f, result.value, 1e-4);
    }

    @Test
    void alignsWithAnotherElementsStart() {
        Snapping.Result result = Snapping.snap(122.0f, 40.0f, 400.0f,
                new float[]{120.0f}, new float[]{60.0f}, THRESHOLD, MARGIN);
        assertTrue(result.snapped);
        assertEquals(120.0f, result.value, 1e-4);
    }

    @Test
    void alignsWithAnotherElementsEnd() {
        // other spans 120..180, so ends align at 180 - 40 = 140
        Snapping.Result result = Snapping.snap(141.5f, 40.0f, 400.0f,
                new float[]{120.0f}, new float[]{60.0f}, THRESHOLD, MARGIN);
        assertTrue(result.snapped);
        assertEquals(140.0f, result.value, 1e-4);
    }

    @Test
    void buttsUpAgainstAnotherElement() {
        Snapping.Result result = Snapping.snap(178.0f, 40.0f, 400.0f,
                new float[]{120.0f}, new float[]{60.0f}, THRESHOLD, MARGIN);
        assertTrue(result.snapped);
        assertEquals(180.0f, result.value, 1e-4);
    }

    @Test
    void choosesTheNearestOfCompetingTargets() {
        // Starts-aligned at 120 and centres-aligned at 130; a candidate at 129 must take 130,
        // otherwise an element between two guides flickers between them while dragging.
        Snapping.Result result = Snapping.snap(129.0f, 40.0f, 400.0f,
                new float[]{120.0f}, new float[]{60.0f}, THRESHOLD, MARGIN);
        assertTrue(result.snapped);
        assertEquals(130.0f, result.value, 1e-4);
    }

    @Test
    void neverPlacesAnElementOffScreen() {
        // Aligning ends with a much smaller element would put this one at a negative position.
        Snapping.Result result = Snapping.snap(1.0f, 300.0f, 400.0f,
                new float[]{2.0f}, new float[]{10.0f}, THRESHOLD, MARGIN);
        assertTrue(result.value >= 0.0f, "must not go off the left edge");
        assertTrue(result.value + 300.0f <= 400.0f, "must not go off the right edge");
    }

    @Test
    void anElementWiderThanTheScreenPinsToZero() {
        Snapping.Result result = snap(-40.0f, 500.0f, 400.0f);
        assertEquals(0.0f, result.value, 1e-4);
    }

    @Test
    void mismatchedInputLengthsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Snapping.snap(
                10.0f, 10.0f, 400.0f, new float[]{1.0f}, new float[]{1.0f, 2.0f}, THRESHOLD, MARGIN));
    }
}
