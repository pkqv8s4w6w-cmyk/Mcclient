package dev.vantage.combat;

import java.util.Arrays;

/**
 * One enemy's clicking, and when their next swing will come.
 *
 * <p>A player fighting clicks at a fairly steady rate, so the gaps between the swings you have seen
 * say when the next one is due. The typical gap is the median of the recent ones, which a single
 * double-click or hesitation does not move. Nothing is predicted until a rhythm has been seen, and
 * predictions stop a couple of gaps after the last swing: someone who stopped clicking is not
 * about to hit you.
 *
 * <p>Swings arrive on the network thread and predictions are asked for on the game thread, so every
 * method is synchronized. Minecraft-free and clocked by the caller, so it is tested directly.
 */
public final class SwingRhythm {

    /** How many recent swings are kept. */
    static final int KEEP = 8;
    /** A gap longer than this is a pause in the fight, not part of a rhythm. */
    static final long MAX_INTERVAL_MILLIS = 600L;
    /** How many gaps in a row a rhythm needs before it predicts anything. */
    static final int MIN_INTERVALS = 2;
    /** Predictions run out this many gaps after the last swing seen. */
    static final int MAX_MISSED = 2;

    private final long[] times = new long[KEEP];
    private int count;

    /** Records a swing seen at {@code time}. */
    public synchronized void swing(long time) {
        if (count > 0 && time <= times[(count - 1) % KEEP]) {
            return;
        }
        times[count % KEEP] = time;
        count++;
    }

    public synchronized long lastSwing() {
        return count == 0 ? -1L : times[(count - 1) % KEEP];
    }

    /**
     * The typical gap between swings in the current run of clicking, or -1 if there is no steady
     * run to go by.
     */
    public synchronized long interval() {
        int stored = Math.min(count, KEEP);
        long[] gaps = new long[stored];
        int found = 0;
        // Walk back from the newest swing; the run ends at the first pause.
        for (int i = count - 1; i > count - stored; i--) {
            long gap = times[i % KEEP] - times[(i - 1) % KEEP];
            if (gap > MAX_INTERVAL_MILLIS) {
                break;
            }
            gaps[found++] = gap;
        }
        if (found < MIN_INTERVALS) {
            return -1L;
        }
        long[] run = Arrays.copyOf(gaps, found);
        Arrays.sort(run);
        return run[found / 2];
    }

    /**
     * When the first swing at or after {@code notBefore} is expected to be seen, or -1 if this
     * player has no rhythm or has stopped clicking before then.
     */
    public synchronized long nextSwing(long notBefore) {
        long interval = interval();
        if (interval <= 0L) {
            return -1L;
        }
        long last = lastSwing();
        long expected = last + interval;
        if (expected < notBefore) {
            expected += ((notBefore - expected + interval - 1) / interval) * interval;
        }
        return expected - last > interval * MAX_MISSED ? -1L : expected;
    }

    /**
     * Whether to be blocking at {@code now} for a swing expected to be seen at {@code seenAt}.
     *
     * <p>You see a swing half a round trip after the server took it, and your block reaches the
     * server half a round trip after you start it. So a block has to begin a full round trip before
     * the swing is expected to be seen, a little earlier for safety, and it is held a little past
     * that point because nobody clicks exactly on the beat.
     *
     * @param ping     your round trip to the server, in milliseconds
     * @param interval the attacker's typical gap between swings
     */
    public static boolean inBlockWindow(long now, long seenAt, long ping, long interval) {
        long slack = slack(interval);
        long serverDeadline = seenAt - ping;
        return now >= serverDeadline - slack && now <= serverDeadline + slack;
    }

    /** How far either side of a predicted swing it may really fall. */
    public static long slack(long interval) {
        return Math.max(60L, Math.round(interval * 0.35));
    }
}
