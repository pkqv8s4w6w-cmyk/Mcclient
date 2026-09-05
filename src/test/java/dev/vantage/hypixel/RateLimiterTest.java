package dev.vantage.hypixel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void spendsItsBudgetThenRefuses() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(3, 60_000L, clock);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void refillsWhenTheWindowElapses() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(2, 60_000L, clock);
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertFalse(limiter.tryAcquire());

        clock.advance(60_001L);
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void serverFiguresOverrideTheLocalEstimate() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(300, 300_000L, clock);
        limiter.tryAcquire();
        assertEquals(299, limiter.getRemaining());

        // Another tool is sharing this key and has spent most of it.
        limiter.adopt(4, 120);
        assertEquals(4, limiter.getRemaining());
        assertEquals(120_000L, limiter.millisUntilReset());
    }

    @Test
    void absentHeadersLeaveTheEstimateAlone() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(10, 60_000L, clock);
        limiter.tryAcquire();
        limiter.adopt(null, null);
        assertEquals(9, limiter.getRemaining());
    }

    @Test
    void exhaustBlocksUntilTheReportedReset() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(100, 300_000L, clock);
        limiter.exhaust(30);
        assertFalse(limiter.tryAcquire());

        clock.advance(29_000L);
        assertFalse(limiter.tryAcquire());
        clock.advance(2_000L);
        assertTrue(limiter.tryAcquire());
    }
}
