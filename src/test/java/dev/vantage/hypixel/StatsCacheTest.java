package dev.vantage.hypixel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsCacheTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void servesFromCacheUntilTheEntryExpires() {
        MutableClock clock = new MutableClock();
        StatsCache cache = new StatsCache(clock, 10_000L);
        cache.store(PLAYER, BedwarsStats.UNKNOWN);

        assertNotNull(cache.get(PLAYER));
        assertFalse(cache.needsFetch(PLAYER));

        clock.advance(10_001L);
        assertNull(cache.get(PLAYER));
        assertTrue(cache.needsFetch(PLAYER));
    }

    @Test
    void onlyOneCallerCanClaimAPlayer() {
        StatsCache cache = new StatsCache(new MutableClock(), 10_000L);
        assertTrue(cache.claim(PLAYER), "first caller should win");
        assertFalse(cache.claim(PLAYER), "a second thread must not duplicate the request");
        assertFalse(cache.needsFetch(PLAYER), "in flight counts as covered");
    }

    @Test
    void storingReleasesTheClaim() {
        StatsCache cache = new StatsCache(new MutableClock(), 10_000L);
        cache.claim(PLAYER);
        cache.store(PLAYER, BedwarsStats.UNKNOWN);
        assertFalse(cache.needsFetch(PLAYER));
    }

    @Test
    void releasingAnUnsentRequestAllowsARetry() {
        StatsCache cache = new StatsCache(new MutableClock(), 10_000L);
        cache.claim(PLAYER);
        cache.release(PLAYER);
        assertTrue(cache.needsFetch(PLAYER));
    }

    @Test
    void failuresAreRememberedBrieflySoTheyAreNotRetriedEveryTick() {
        MutableClock clock = new MutableClock();
        StatsCache cache = new StatsCache(clock, 10 * 60 * 1000L);
        cache.storeFailure(PLAYER);

        assertTrue(cache.isFailed(PLAYER));
        assertFalse(cache.needsFetch(PLAYER), "must not hammer a failing lookup");

        // The failure window is far shorter than the success window.
        clock.advance(61_000L);
        assertTrue(cache.needsFetch(PLAYER), "should be willing to try again after a minute");
        assertFalse(cache.isFailed(PLAYER));
    }

    @Test
    void pruningDropsExpiredEntries() {
        MutableClock clock = new MutableClock();
        StatsCache cache = new StatsCache(clock, 5_000L);
        cache.store(UUID.randomUUID(), BedwarsStats.UNKNOWN);
        cache.store(UUID.randomUUID(), BedwarsStats.UNKNOWN);
        assertEquals(2, cache.size());

        clock.advance(5_001L);
        cache.prune();
        assertEquals(0, cache.size());
    }
}
