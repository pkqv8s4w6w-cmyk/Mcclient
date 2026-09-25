package dev.vantage.net;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldPacketQueueTest {

    /** Stand-in for a packet; the queue never looks inside one. */
    private static Object packet(String label) {
        return label;
    }

    private static List<String> labels(List<HeldPacketQueue.Held> held) {
        java.util.List<String> result = new java.util.ArrayList<String>(held.size());
        for (HeldPacketQueue.Held entry : held) {
            result.add((String) entry.packet);
        }
        return result;
    }

    @Test
    void nothingComesOutBeforeItsReleaseTime() {
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("a"), 7, 1000L, 200L);

        assertTrue(queue.drainDue(1199L).isEmpty(), "still inside the delay");
        assertEquals(1, queue.size());
        assertEquals(1, queue.drainDue(1200L).size(), "due on the boundary, not a tick later");
        assertTrue(queue.isEmpty());
    }

    @Test
    void packetsComeOutInTheOrderTheyWentIn() {
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("a"), 7, 1000L, 200L);
        queue.hold(packet("b"), 7, 1050L, 200L);
        queue.hold(packet("c"), 7, 1100L, 200L);

        assertEquals(java.util.Arrays.asList("a", "b", "c"), labels(queue.drainDue(1300L)));
    }

    @Test
    void shrinkingTheDelayCannotLetALaterPacketOvertakeAnEarlierOne() {
        // The slider moved from 500ms to nothing while a packet was still in flight. Relative
        // moves applied out of order leave the entity permanently offset, so the second packet
        // has to wait behind the first rather than jumping the queue.
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("first"), 7, 1000L, 500L);
        queue.hold(packet("second"), 7, 1100L, 0L);

        assertTrue(queue.drainDue(1100L).isEmpty(), "the second must not go out ahead of the first");
        assertEquals(java.util.Arrays.asList("first"), labels(queue.drainDue(1500L)));
        assertEquals(java.util.Arrays.asList("second"), labels(queue.drainDue(1501L)));
    }

    @Test
    void aFullQueueReleasesTheOldestRatherThanDroppingIt() {
        // A discarded relative move is a displacement the entity never gets back.
        HeldPacketQueue queue = new HeldPacketQueue(3);
        queue.hold(packet("a"), 7, 1000L, 5000L);
        queue.hold(packet("b"), 7, 1001L, 5000L);
        queue.hold(packet("c"), 7, 1002L, 5000L);

        List<HeldPacketQueue.Held> forced = queue.hold(packet("d"), 7, 1003L, 5000L);

        assertEquals(java.util.Arrays.asList("a"), labels(forced), "handed back for immediate delivery");
        assertEquals(3, queue.size());
        assertEquals(java.util.Arrays.asList("b", "c", "d"), labels(queue.drainAll()));
    }

    @Test
    void theEntryJustAddedIsNeverTheOneForcedOut() {
        HeldPacketQueue queue = new HeldPacketQueue(1);
        queue.hold(packet("a"), 7, 1000L, 5000L);

        List<HeldPacketQueue.Held> forced = queue.hold(packet("b"), 7, 1001L, 5000L);

        assertEquals(java.util.Arrays.asList("a"), labels(forced));
        assertEquals(java.util.Arrays.asList("b"), labels(queue.drainAll()));
    }

    @Test
    void drainAllEmptiesInOrderWhateverTheClockSays() {
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("a"), 7, 1000L, 10_000L);
        queue.hold(packet("b"), 7, 1001L, 10_000L);

        assertEquals(java.util.Arrays.asList("a", "b"), labels(queue.drainAll()));
        assertTrue(queue.isEmpty());
    }

    @Test
    void aFlushDoesNotLeaveTheNextPacketWaitingOnTheOldSchedule() {
        // drainAll cleared a packet that was not due until 6000. If the queue kept treating that
        // as the tail, the next packet would inherit the wait and the module would look frozen.
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("stale"), 7, 1000L, 5000L);
        queue.drainAll();

        queue.hold(packet("fresh"), 7, 1010L, 0L);

        assertEquals(java.util.Arrays.asList("fresh"), labels(queue.drainDue(1010L)));
    }

    @Test
    void aZeroDelayGoesOutOnTheNextDrainRatherThanBeingSwallowed() {
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("a"), 7, 1000L, 0L);

        assertEquals(1, queue.drainDue(1000L).size());
    }

    @Test
    void aNegativeDelayIsTreatedAsNoDelay() {
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("a"), 7, 1000L, -250L);

        assertEquals(1, queue.drainDue(1000L).size());
    }

    @Test
    void theEntityAndThePacketSurviveTheRoundTrip() {
        HeldPacketQueue queue = new HeldPacketQueue();
        Object original = packet("a");
        queue.hold(original, 42, 1000L, 100L);

        HeldPacketQueue.Held held = queue.drainDue(1100L).get(0);

        assertSame(original, held.packet);
        assertEquals(42, held.entityId);
        assertEquals(1100L, held.releaseAtMillis);
    }

    @Test
    void theNextReleaseTimeTracksTheHead() {
        HeldPacketQueue queue = new HeldPacketQueue();
        assertNull(queue.nextReleaseAtMillis(), "nothing waiting");

        queue.hold(packet("a"), 7, 1000L, 200L);
        queue.hold(packet("b"), 7, 1000L, 400L);
        assertEquals(Long.valueOf(1200L), queue.nextReleaseAtMillis());

        queue.drainDue(1200L);
        assertEquals(Long.valueOf(1400L), queue.nextReleaseAtMillis());
    }

    @Test
    void discardingThrowsTheContentsAway() {
        HeldPacketQueue queue = new HeldPacketQueue();
        queue.hold(packet("a"), 7, 1000L, 200L);

        queue.discardAll();

        assertTrue(queue.isEmpty());
        assertTrue(queue.drainAll().isEmpty());
        assertNull(queue.nextReleaseAtMillis());
    }

    @Test
    void aQueueMustHaveRoomForAtLeastOnePacket() {
        try {
            new HeldPacketQueue(0);
            org.junit.jupiter.api.Assertions.fail("a zero-capacity queue would drop every packet");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }
}
