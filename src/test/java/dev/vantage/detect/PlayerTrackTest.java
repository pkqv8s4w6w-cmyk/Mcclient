package dev.vantage.detect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regression these exist for: the detector used to read its evidence windows once a second
 * without ever clearing them, so a single odd stretch of play was re-reported over and over,
 * adding to the same suspicion score each time. Nobody had to do anything twice to be accused of
 * it — they only had to do it once and stay on screen. That is why most of a lobby got flagged.
 */
class PlayerTrackTest {

    @Test
    void evidenceAccumulatesUntilThereIsEnoughToJudge() {
        PlayerTrack track = new PlayerTrack();
        for (int i = 0; i < 5; i++) {
            track.recordSwing(1_000L + i * 90L);
        }
        assertNull(track.takeSwings(10), "not enough to reach a verdict yet");

        for (int i = 5; i < 12; i++) {
            track.recordSwing(1_000L + i * 90L);
        }
        long[] taken = track.takeSwings(10);
        assertNotNull(taken);
        assertEquals(12, taken.length, "the earlier samples must still be there");
    }

    @Test
    void askingWithoutEnoughEvidenceDoesNotThrowItAway() {
        PlayerTrack track = new PlayerTrack();
        track.recordSwing(1_000L);
        track.recordSwing(1_090L);

        assertNull(track.takeSwings(10));
        assertNull(track.takeSwings(10));
        track.recordSwing(1_180L);

        long[] taken = track.takeSwings(3);
        assertEquals(3, taken.length, "three failed reads must not have consumed anything");
    }

    @Test
    void judgingEvidenceSpendsIt() {
        // The fix. Once a window has been judged it is gone, so the same behaviour cannot be
        // counted against somebody a second time a second later.
        PlayerTrack track = new PlayerTrack();
        for (int i = 0; i < 12; i++) {
            track.recordSwing(1_000L + i * 90L);
        }
        assertEquals(12, track.takeSwings(10).length);
        assertNull(track.takeSwings(10), "the same swings must not be judged twice");
    }

    @Test
    void everyKindOfEvidenceIsSpentTheSameWay() {
        PlayerTrack track = new PlayerTrack();
        for (int i = 0; i < 12; i++) {
            track.recordRotation(i * 5.0, 10.0, 1_000L + i * 50L);
            track.recordPlacement(10.0, 120.0);
            track.recordMovement(new MovementSample(i, 64.0, 0.0, true, true, 1.0, false));
            track.recordHitDisplacement(0.4);
        }

        assertNotNull(track.takeRotations(5));
        assertNull(track.takeRotations(5));

        assertNotNull(track.takePlacements(5));
        assertNull(track.takePlacements(5));

        assertNotNull(track.takeMovement(5));
        assertNull(track.takeMovement(5));

        assertNotNull(track.takeHitDisplacements(5));
        assertNull(track.takeHitDisplacements(5));
    }

    @Test
    void hitsAreSharedByTwoChecksThenSpentOnce() {
        // Reach and backtrack read the same hits and have to see the same ones, so these are taken
        // by peeking and cleared deliberately rather than consumed by the first reader.
        PlayerTrack track = new PlayerTrack();
        for (int i = 0; i < 12; i++) {
            track.recordHitOnYou(new HitSample(4.0, 2.7, 6));
        }
        List<HitSample> forReach = track.peekHits(10);
        List<HitSample> forBacktrack = track.peekHits(10);
        assertNotNull(forReach);
        assertEquals(forReach.size(), forBacktrack.size());

        track.clearHits();
        assertNull(track.peekHits(10), "and once both have had their say, they are gone");
    }

    @Test
    void rotationsComeBackInOrderAndPaired() {
        PlayerTrack track = new PlayerTrack();
        // The first call only establishes a reference yaw; the delta needs two.
        track.recordRotation(0.0, 40.0, 0L);
        track.recordRotation(10.0, 30.0, 50L);
        track.recordRotation(25.0, 15.0, 100L);

        double[][] taken = track.takeRotations(2);
        assertEquals(10.0, taken[0][0], 1e-9);
        assertEquals(30.0, taken[1][0], 1e-9);
        assertEquals(15.0, taken[0][1], 1e-9);
        assertEquals(15.0, taken[1][1], 1e-9);
    }

    @Test
    void aGapBetweenFightsDoesNotInventAnEnormousTurn() {
        // Without this, the first rotation of the next fight is differenced against a yaw from
        // whenever the last one ended, putting one invented turn of any size into the sample.
        PlayerTrack track = new PlayerTrack();
        track.recordRotation(0.0, 20.0, 0L);
        track.recordRotation(5.0, 15.0, 50L);

        track.breakRotationContinuity();

        track.recordRotation(170.0, 20.0, 60_000L);
        track.recordRotation(175.0, 15.0, 60_050L);

        double[][] taken = track.takeRotations(2);
        for (double delta : taken[0]) {
            assertTrue(Math.abs(delta) <= 10.0, "invented a turn of " + delta);
        }
    }

    @Test
    void yawWrapsTheShortWayRound() {
        // Passing due south is a two degree turn, not a three hundred and fifty eight degree one.
        assertEquals(-2.0, PlayerTrack.wrapDegrees(358.0), 1e-9);
        assertEquals(2.0, PlayerTrack.wrapDegrees(-358.0), 1e-9);
        assertEquals(0.0, PlayerTrack.wrapDegrees(720.0), 1e-9);
    }

    @Test
    void olderEvidenceFallsOffRatherThanGrowingWithoutBound() {
        PlayerTrack track = new PlayerTrack();
        for (int i = 0; i < 5_000; i++) {
            track.recordSwing(i);
            track.recordHitOnYou(new HitSample(3.0, 3.0, 0));
        }
        assertEquals(128, track.takeSwings(1).length);
        assertEquals(32, track.hitCount());
    }

    @Test
    void clearingEmptiesEverything() {
        PlayerTrack track = new PlayerTrack();
        for (int i = 0; i < 12; i++) {
            track.recordSwing(i * 90L);
            track.recordHitOnYou(new HitSample(3.0, 3.0, 0));
        }
        track.clear();
        assertNull(track.takeSwings(1));
        assertEquals(0, track.hitCount());
    }
}
