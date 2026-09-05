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
    private static final int MOVEMENT_CAPACITY = 100;
    private static final int PLACEMENT_CAPACITY = 40;
    private static final int HIT_CAPACITY = 16;

    private final double[] yawDeltas = new double[ROTATION_CAPACITY];
    private final double[] targetAngles = new double[ROTATION_CAPACITY];
    private int rotationCount;
    private int rotationHead;

    private final long[] swingTimes = new long[SWING_CAPACITY];
    private int swingCount;
    private int swingHead;

    private final java.util.ArrayDeque<MovementSample> movement =
            new java.util.ArrayDeque<MovementSample>(MOVEMENT_CAPACITY);

    private final java.util.ArrayDeque<double[]> placements =
            new java.util.ArrayDeque<double[]>(PLACEMENT_CAPACITY);

    private final java.util.ArrayDeque<Double> hitDisplacements =
            new java.util.ArrayDeque<Double>(HIT_CAPACITY);

    private final java.util.ArrayDeque<Double> hitsOnYou =
            new java.util.ArrayDeque<Double>(HIT_CAPACITY);

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

    // -- movement ---------------------------------------------------------------------------

    public synchronized void recordMovement(MovementSample sample) {
        if (movement.size() >= MOVEMENT_CAPACITY) {
            movement.removeFirst();
        }
        movement.addLast(sample);
    }

    public synchronized java.util.List<MovementSample> getMovementSamples() {
        return new java.util.ArrayList<MovementSample>(movement);
    }

    // -- block placements -------------------------------------------------------------------

    /**
     * @param pitch        their view pitch when the block appeared, degrees
     * @param angleToBlock angle between their view and the block, degrees
     */
    public synchronized void recordPlacement(double pitch, double angleToBlock) {
        if (placements.size() >= PLACEMENT_CAPACITY) {
            placements.removeFirst();
        }
        placements.addLast(new double[]{pitch, angleToBlock});
    }

    public synchronized double[] getPlacementPitches() {
        double[] out = new double[placements.size()];
        int i = 0;
        for (double[] entry : placements) {
            out[i++] = entry[0];
        }
        return out;
    }

    public synchronized double[] getPlacementAngles() {
        double[] out = new double[placements.size()];
        int i = 0;
        for (double[] entry : placements) {
            out[i++] = entry[1];
        }
        return out;
    }

    // -- reach ------------------------------------------------------------------------------

    /** Distance of a hit this player landed on you. Only hits on you can be measured honestly. */
    public synchronized void recordHitOnYou(double distance) {
        if (hitsOnYou.size() >= HIT_CAPACITY) {
            hitsOnYou.removeFirst();
        }
        hitsOnYou.addLast(distance);
    }

    public synchronized double[] getHitsOnYou() {
        double[] out = new double[hitsOnYou.size()];
        int i = 0;
        for (Double value : hitsOnYou) {
            out[i++] = value;
        }
        return out;
    }

    // -- knockback --------------------------------------------------------------------------

    /** How far they moved in the ticks after taking a hit. */
    public synchronized void recordHitDisplacement(double blocks) {
        if (hitDisplacements.size() >= HIT_CAPACITY) {
            hitDisplacements.removeFirst();
        }
        hitDisplacements.addLast(blocks);
    }

    public synchronized double[] getHitDisplacements() {
        double[] out = new double[hitDisplacements.size()];
        int i = 0;
        for (Double value : hitDisplacements) {
            out[i++] = value;
        }
        return out;
    }

    public synchronized void clear() {
        rotationCount = 0;
        rotationHead = 0;
        swingCount = 0;
        swingHead = 0;
        hasLastYaw = false;
        movement.clear();
        placements.clear();
        hitDisplacements.clear();
        hitsOnYou.clear();
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
