package dev.vantage.hypixel;

/** A clock the tests drive by hand, so nothing has to wait out a real rate limit window. */
class MutableClock implements RateLimiter.Clock {

    private long now = 1_000_000L;

    @Override
    public long millis() {
        return now;
    }

    void advance(long millis) {
        now += millis;
    }
}
