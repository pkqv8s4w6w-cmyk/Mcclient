package dev.vantage.hypixel;

/**
 * Keeps request rate inside the API key's budget.
 *
 * <p>Tracks a local estimate but defers to the server whenever a response reports the real figures,
 * since the local count drifts as soon as anything else uses the same key. The clock is injectable
 * so the behaviour can be tested without waiting out a window.
 */
public final class RateLimiter {

    /** Wall clock in milliseconds. Separated out so tests can drive time directly. */
    public interface Clock {
        long millis();
    }

    public static final Clock SYSTEM = new Clock() {
        @Override
        public long millis() {
            return System.currentTimeMillis();
        }
    };

    private final int budget;
    private final long windowMillis;
    private final Clock clock;

    private int remaining;
    private long windowEndsAt;

    public RateLimiter(int budget, long windowMillis, Clock clock) {
        this.budget = Math.max(1, budget);
        this.windowMillis = Math.max(1L, windowMillis);
        this.clock = clock;
        this.remaining = this.budget;
        this.windowEndsAt = clock.millis() + this.windowMillis;
    }

    /** Hypixel's documented default: 300 requests in a five minute window. */
    public static RateLimiter hypixelDefault(Clock clock) {
        return new RateLimiter(300, 300_000L, clock);
    }

    public synchronized boolean tryAcquire() {
        rolloverIfElapsed();
        if (remaining <= 0) {
            return false;
        }
        remaining--;
        return true;
    }

    private void rolloverIfElapsed() {
        long now = clock.millis();
        if (now >= windowEndsAt) {
            remaining = budget;
            windowEndsAt = now + windowMillis;
        }
    }

    /**
     * Adopts the figures a response reported.
     *
     * <p>Always trusted over the local estimate: another tool sharing the key, or a request this
     * client never saw, both leave the local count optimistic, and being optimistic about a rate
     * limit is how a key gets throttled.
     *
     * @param serverRemaining value of the RateLimit-Remaining header, or null if absent
     * @param resetSeconds    value of the RateLimit-Reset header, or null if absent
     */
    public synchronized void adopt(Integer serverRemaining, Integer resetSeconds) {
        if (serverRemaining != null && serverRemaining >= 0) {
            remaining = serverRemaining;
        }
        if (resetSeconds != null && resetSeconds >= 0) {
            windowEndsAt = clock.millis() + resetSeconds * 1000L;
        }
    }

    /** Marks the budget spent until the window rolls over, after a 429. */
    public synchronized void exhaust(Integer resetSeconds) {
        remaining = 0;
        if (resetSeconds != null && resetSeconds > 0) {
            windowEndsAt = clock.millis() + resetSeconds * 1000L;
        }
    }

    public synchronized int getRemaining() {
        rolloverIfElapsed();
        return remaining;
    }

    public synchronized long millisUntilReset() {
        return Math.max(0L, windowEndsAt - clock.millis());
    }
}
