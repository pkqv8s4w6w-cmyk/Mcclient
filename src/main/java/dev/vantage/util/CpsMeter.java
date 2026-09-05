package dev.vantage.util;

/**
 * Counts the local player's own clicks over a rolling one second window, for the CPS display.
 *
 * <p>Not part of the cheat detector. That watches other players' swing packet timings and lives in
 * {@code dev.vantage.detect.ClickAnalysis}; this only feeds a number on your own screen.
 *
 * <p>A ring buffer of timestamps rather than a counter reset each second, so the reading is a true
 * rate at any instant instead of jumping to zero on a boundary.
 */
public final class CpsMeter {

    private static final int CAPACITY = 64;
    private static final long WINDOW_MILLIS = 1000L;

    private final long[] times = new long[CAPACITY];
    private int count;
    private int head;

    public void click(long nowMillis) {
        times[head] = nowMillis;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
    }

    /** Clicks in the last second. */
    public int perSecond(long nowMillis) {
        long cutoff = nowMillis - WINDOW_MILLIS;
        int total = 0;
        for (int i = 0; i < count; i++) {
            if (times[i] > cutoff) {
                total++;
            }
        }
        return total;
    }

    public void reset() {
        count = 0;
        head = 0;
    }
}
