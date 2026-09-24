package dev.vantage.hypixel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers what has already been looked up.
 *
 * <p>Three jobs: stop a sixteen player lobby costing sixty requests when the tab list updates
 * repeatedly, keep a failed lookup from being retried every tick, and prevent two threads chasing
 * the same player at once.
 */
public final class StatsCache {

    private static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000L;

    /** Failures are remembered for much less time, since they are usually transient. */
    private static final long FAILURE_TTL_MILLIS = 60 * 1000L;

    private static final class Entry {
        final BedwarsStats stats;
        final long expiresAt;
        final boolean failed;

        Entry(BedwarsStats stats, long expiresAt, boolean failed) {
            this.stats = stats;
            this.expiresAt = expiresAt;
            this.failed = failed;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<UUID, Entry>();
    private final Set<UUID> inFlight = new HashSet<UUID>();
    private final RateLimiter.Clock clock;
    private final long ttlMillis;

    public StatsCache(RateLimiter.Clock clock) {
        this(clock, DEFAULT_TTL_MILLIS);
    }

    public StatsCache(RateLimiter.Clock clock, long ttlMillis) {
        this.clock = clock;
        this.ttlMillis = ttlMillis;
    }

    /** @return cached stats, or null when nothing usable is held */
    public synchronized BedwarsStats get(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null || clock.millis() >= entry.expiresAt) {
            return null;
        }
        return entry.stats;
    }

    /** True when this player is worth requesting: nothing fresh cached and nothing in flight. */
    public synchronized boolean needsFetch(UUID uuid) {
        if (inFlight.contains(uuid)) {
            return false;
        }
        Entry entry = entries.get(uuid);
        return entry == null || clock.millis() >= entry.expiresAt;
    }

    /**
     * Claims a player for fetching.
     *
     * @return false if someone else already claimed it, in which case do not make the request
     */
    public synchronized boolean claim(UUID uuid) {
        if (!needsFetch(uuid)) {
            return false;
        }
        inFlight.add(uuid);
        return true;
    }

    public synchronized void store(UUID uuid, BedwarsStats stats) {
        entries.put(uuid, new Entry(stats, clock.millis() + ttlMillis, false));
        inFlight.remove(uuid);
    }

    /** Records a failed lookup so it is not retried immediately. */
    public synchronized void storeFailure(UUID uuid) {
        entries.put(uuid, new Entry(BedwarsStats.UNKNOWN, clock.millis() + FAILURE_TTL_MILLIS, true));
        inFlight.remove(uuid);
    }

    /** Releases a claim without recording anything, for a request that was never sent. */
    public synchronized void release(UUID uuid) {
        inFlight.remove(uuid);
    }

    public synchronized boolean isFailed(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry != null && entry.failed && clock.millis() < entry.expiresAt;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
        inFlight.clear();
    }

    /** Drops expired entries so a long session does not grow the map without bound. */
    public synchronized void prune() {
        long now = clock.millis();
        entries.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt);
    }
}
