package dev.vantage.detect;

/**
 * A rolling window of one player's recent behaviour.
 *
 * <p>Fixed-size ring buffers, so a long game costs the same memory as a short one. Swings are
 * recorded from the network thread while the rotation samples come from the client thread, so
 * every method is synchronised.
 */
public final class PlayerTrack {

    private static final int ROTATION_CAPACITY = 100; // five seconds at twenty ticks
    private static final int SWING_CAPACITY = 64;

    private final double[] yawDeltas = new double[ROTATION_CAPACITY];
    private final double[] targetAngles = new double[ROTATION_CAPACITY];
    private int rotationCount;
    private int rotationHead;

    private final long[] swingTimes = new long[SWING_CAPACITY];
    private int swingCount;
    private int swingHead;

    private double lastYaw;
    private boolean hasLastYaw;
    private long lastSeenMillis;

    public synchronized void recordRotation(double yaw, double angleToTarget, long nowMillis) {
        lastSeenMillis = nowMillis;
        if (!hasLastYaw) {
            lastYaw = yaw;
            hasLastYaw = true;
            return;
        }
        yawDeltas[rotationHead] = wrapDegrees(yaw - lastYaw);
        targetAngles[rotationHead] = angleToTarget;
        rotationHead = (rotationHead + 1) % ROTATION_CAPACITY;
        if (rotationCount < ROTATION_CAPACITY) {
            rotationCount++;
        }
        lastYaw = yaw;
    }

    public synchronized void recordSwing(long nowMillis) {
        swingTimes[swingHead] = nowMillis;
        swingHead = (swingHead + 1) % SWING_CAPACITY;
        if (swingCount < SWING_CAPACITY) {
            swingCount++;
        }
    }

    /** Rotation deltas oldest first. */
    public synchronized double[] getYawDeltas() {
        return drain(yawDeltas, rotationCount, rotationHead);
    }

    /** Angles to the nearest target, aligned with {@link #getYawDeltas()}. */
    public synchronized double[] getTargetAngles() {
        return drain(targetAngles, rotationCount, rotationHead);
    }

    public synchronized long[] getSwingTimes() {
        long[] out = new long[swingCount];
        int start = (swingHead - swingCount + SWING_CAPACITY) % SWING_CAPACITY;
        for (int i = 0; i < swingCount; i++) {
            out[i] = swingTimes[(start + i) % SWING_CAPACITY];
        }
        return out;
    }

    public synchronized long getLastSeenMillis() {
        return lastSeenMillis;
    }

    public synchronized void clear() {
        rotationCount = 0;
        rotationHead = 0;
        swingCount = 0;
        swingHead = 0;
        hasLastYaw = false;
    }

    private static double[] drain(double[] buffer, int count, int head) {
        double[] out = new double[count];
        int capacity = buffer.length;
        int start = (head - count + capacity) % capacity;
        for (int i = 0; i < count; i++) {
            out[i] = buffer[(start + i) % capacity];
        }
        return out;
    }

    /** Brings a yaw difference into -180..180, so passing due south is not a 359 degree turn. */
    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }
}
