package dev.vantage.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A FIFO buffer of packets waiting to be handed to the game.
 *
 * <p>Plain data with no Minecraft types, so the ordering rules below can be tested against a fake
 * clock rather than discovered in a fight. {@link PacketDelayer} owns the Netty side.
 *
 * <p>Two rules carry the whole thing, and both exist because entity movement in 1.8.9 is mostly
 * <em>relative</em>. {@code S14PacketEntity} says "move that player 0.3 blocks east", not "that
 * player is here", so the stream only makes sense applied in order and in full:
 *
 * <ul>
 *   <li><b>Release times never go backwards.</b> A packet is scheduled for {@code now + delay}, or
 *       one millisecond after the packet in front of it, whichever is later. Without that, dropping
 *       the delay slider mid-fight lets a newer packet overtake an older one and the player ends up
 *       permanently offset from where the server has them.</li>
 *   <li><b>A full queue releases early, it never discards.</b> Dropping a relative move loses that
 *       displacement for good - nothing later in the stream corrects it short of a teleport. So
 *       overflow hands the oldest entries back to be delivered immediately, which costs some of the
 *       delay and nothing else.</li>
 * </ul>
 */
public final class HeldPacketQueue {

    /**
     * Roughly ten seconds of a busy player's movement packets. Reaching this means something has
     * gone wrong - a stalled event loop, or a delay far past anything useful - and releasing early
     * is the safe way out.
     */
    public static final int DEFAULT_CAPACITY = 512;

    /** A packet waiting on the clock, and the entity it belongs to. */
    public static final class Held {
        public final Object packet;
        public final int entityId;
        public final long releaseAtMillis;

        Held(Object packet, int entityId, long releaseAtMillis) {
            this.packet = packet;
            this.entityId = entityId;
            this.releaseAtMillis = releaseAtMillis;
        }
    }

    private final Deque<Held> queue = new ArrayDeque<Held>();
    private final int capacity;

    /**
     * Release time of the most recently queued packet, or {@link Long#MIN_VALUE} when the queue is
     * empty. Reset on empty because nothing is left to overtake, so the next packet is free to use
     * the delay it was actually given.
     */
    private long tailReleaseAtMillis = Long.MIN_VALUE;

    public HeldPacketQueue() {
        this(DEFAULT_CAPACITY);
    }

    public HeldPacketQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least one");
        }
        this.capacity = capacity;
    }

    /**
     * Queues a packet.
     *
     * @param delayMillis how long to hold it; negative is treated as zero
     * @return the packets forced out to make room, oldest first, which the caller must deliver
     *         before the one it just queued. Usually empty.
     */
    public synchronized List<Held> hold(Object packet, int entityId, long nowMillis, long delayMillis) {
        List<Held> forced = Collections.emptyList();
        // Evict before inserting, so a full queue can never force out the packet just added.
        while (queue.size() >= capacity) {
            if (forced.isEmpty()) {
                forced = new ArrayList<Held>();
            }
            forced.add(queue.pollFirst());
        }

        long requested = nowMillis + Math.max(0L, delayMillis);
        long releaseAt = queue.isEmpty() ? requested : Math.max(requested, tailReleaseAtMillis + 1L);
        queue.addLast(new Held(packet, entityId, releaseAt));
        tailReleaseAtMillis = releaseAt;
        return forced;
    }

    /** Everything at the head whose time has come, oldest first. */
    public synchronized List<Held> drainDue(long nowMillis) {
        List<Held> due = Collections.emptyList();
        while (!queue.isEmpty() && queue.peekFirst().releaseAtMillis <= nowMillis) {
            if (due.isEmpty()) {
                due = new ArrayList<Held>();
            }
            due.add(queue.pollFirst());
        }
        if (queue.isEmpty()) {
            tailReleaseAtMillis = Long.MIN_VALUE;
        }
        return due;
    }

    /** Everything, in order, regardless of the clock. Used when the hold has to end at once. */
    public synchronized List<Held> drainAll() {
        if (queue.isEmpty()) {
            return Collections.emptyList();
        }
        List<Held> all = new ArrayList<Held>(queue.size());
        Held held;
        while ((held = queue.pollFirst()) != null) {
            all.add(held);
        }
        tailReleaseAtMillis = Long.MIN_VALUE;
        return all;
    }

    /** Throws the contents away. Only for a connection that has already gone. */
    public synchronized void discardAll() {
        queue.clear();
        tailReleaseAtMillis = Long.MIN_VALUE;
    }

    /** When the next packet is due, or null when nothing is waiting. */
    public synchronized Long nextReleaseAtMillis() {
        Held head = queue.peekFirst();
        return head == null ? null : head.releaseAtMillis;
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public int getCapacity() {
        return capacity;
    }
}
