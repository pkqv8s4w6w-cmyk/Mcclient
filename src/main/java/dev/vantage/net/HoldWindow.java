package dev.vantage.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player bookkeeping for how long a target may be held, and how long before they may be held
 * again.
 *
 * <p>Plain data with no Minecraft types, so the lifecycle can be tested against a fake clock.
 * {@link PacketDelayer} owns the packets; this owns only the decision.
 *
 * <p>The shape that matters: holding a target is a <em>window</em>, not a running delay. From the
 * moment the first packet is blocked, the target stays pinned where they were until the window runs
 * out — that is what buys the extra hits, and it is why the window has to end on a clock of its own.
 * A held player stops moving on screen, so their distance stops changing too, and no
 * distance check can ever be what ends it.
 *
 * <p>After a window ends the target goes on cooldown, so the effect comes in bursts rather than as
 * one permanent stall on whoever is nearest.
 */
public final class HoldWindow {

    /** Engaged targets, mapped to the moment their window closes. */
    private final Map<Integer, Long> deadlines = new HashMap<Integer, Long>();

    /** Targets that recently finished a window, mapped to when they may be held again. */
    private final Map<Integer, Long> cooldowns = new HashMap<Integer, Long>();

    /**
     * Opens a window for this target, or reports that an open one is still running.
     *
     * @return true while packets for this target should be held; false means the window has closed
     *         and the caller must {@link #end} it
     */
    public boolean beginOrContinue(int entityId, long nowMillis, long maxDelayMillis) {
        Integer key = Integer.valueOf(entityId);
        Long deadline = deadlines.get(key);
        if (deadline == null) {
            if (maxDelayMillis <= 0L) {
                return false;
            }
            deadlines.put(key, Long.valueOf(nowMillis + maxDelayMillis));
            return true;
        }
        return nowMillis < deadline.longValue();
    }

    /** How much of this target's window is left, or zero when it is closed or was never open. */
    public long remaining(int entityId, long nowMillis) {
        Long deadline = deadlines.get(Integer.valueOf(entityId));
        if (deadline == null) {
            return 0L;
        }
        return Math.max(0L, deadline.longValue() - nowMillis);
    }

    public boolean isEngaged(int entityId) {
        return deadlines.containsKey(Integer.valueOf(entityId));
    }

    public boolean isCoolingDown(int entityId, long nowMillis) {
        Long until = cooldowns.get(Integer.valueOf(entityId));
        if (until == null) {
            return false;
        }
        if (nowMillis >= until.longValue()) {
            cooldowns.remove(Integer.valueOf(entityId));
            return false;
        }
        return true;
    }

    /** Closes this target's window and starts their cooldown. Safe to call when not engaged. */
    public void end(int entityId, long nowMillis, long cooldownMillis) {
        Integer key = Integer.valueOf(entityId);
        deadlines.remove(key);
        if (cooldownMillis > 0L) {
            cooldowns.put(key, Long.valueOf(nowMillis + cooldownMillis));
        } else {
            cooldowns.remove(key);
        }
    }

    /**
     * Engaged targets whose window has run out.
     *
     * <p>Needed because a held player generates no further packets to notice the deadline on: once
     * they are pinned, nothing arrives to trigger the check, so someone has to ask on a timer.
     */
    public List<Integer> expired(long nowMillis) {
        List<Integer> done = null;
        for (Map.Entry<Integer, Long> entry : deadlines.entrySet()) {
            if (nowMillis >= entry.getValue().longValue()) {
                if (done == null) {
                    done = new ArrayList<Integer>();
                }
                done.add(entry.getKey());
            }
        }
        return done == null ? java.util.Collections.<Integer>emptyList() : done;
    }

    /** Everyone currently held, as a copy safe to iterate while ending windows. */
    public Set<Integer> engaged() {
        return new HashSet<Integer>(deadlines.keySet());
    }

    /** Drops cooldown entries that have lapsed, so the map cannot grow across a long session. */
    public void pruneCooldowns(long nowMillis) {
        Iterator<Map.Entry<Integer, Long>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            if (nowMillis >= it.next().getValue().longValue()) {
                it.remove();
            }
        }
    }

    public void forget(int entityId) {
        Integer key = Integer.valueOf(entityId);
        deadlines.remove(key);
        cooldowns.remove(key);
    }

    public void clear() {
        deadlines.clear();
        cooldowns.clear();
    }
}
