package dev.vantage.detect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A rolling window of one player's recent behaviour during fights.
 *
 * <p>Everything here is <b>spent when it is judged</b>, and that is the point of the class rather
 * than an implementation detail. The checks used to read a window that was never cleared while
 * running once a second, so a single odd stretch of play was re-reported over and over, adding to
 * the same suspicion score each time until it crossed the threshold. Nobody had to do anything
 * twice to be accused of it; they only had to do it once and then stay on screen. That is why the
 * detector flagged most of a lobby.
 *
 * <p>So each {@code take} method hands the evidence over and clears it — but only once there is
 * enough of it to reach a verdict. Below that it accumulates untouched. Evidence therefore builds
 * up until it can be judged, gets judged exactly once, and is gone.
 *
 * <p>Swings are recorded from the network thread while the rotation samples come from the client
 * thread, so every method is synchronised.
 */
public final class PlayerTrack {

    private static final int ROTATION_CAPACITY = 100; // five seconds at twenty ticks
    private static final int SWING_CAPACITY = 128;
    private static final int MOVEMENT_CAPACITY = 100;
    private static final int PLACEMENT_CAPACITY = 60;
    private static final int HIT_CAPACITY = 32;

    private final Deque<double[]> rotations = new ArrayDeque<double[]>(ROTATION_CAPACITY);
    private final Deque<Long> swings = new ArrayDeque<Long>(SWING_CAPACITY);
    private final Deque<MovementSample> movement = new ArrayDeque<MovementSample>(MOVEMENT_CAPACITY);
    private final Deque<double[]> placements = new ArrayDeque<double[]>(PLACEMENT_CAPACITY);
    private final Deque<HitSample> hitsOnYou = new ArrayDeque<HitSample>(HIT_CAPACITY);
    private final Deque<Double> displacements = new ArrayDeque<Double>(HIT_CAPACITY);

    private double lastYaw;
    private boolean hasLastYaw;
    private long lastSeenMillis;

    // -- rotation ----------------------------------------------------------------------------

    /**
     * @param yaw          their view yaw this tick, degrees
     * @param signedError  horizontal angle from their view to the nearest target, signed so that
     *                     turning past the target changes it, in -180..180
     */
    public synchronized void recordRotation(double yaw, double signedError, long nowMillis) {
        lastSeenMillis = nowMillis;
        if (!hasLastYaw) {
            lastYaw = yaw;
            hasLastYaw = true;
            return;
        }
        push(rotations, new double[]{wrapDegrees(yaw - lastYaw), signedError}, ROTATION_CAPACITY);
        lastYaw = yaw;
    }

    /**
     * Forgets the reference yaw without discarding anything already recorded.
     *
     * <p>Called when a fight ends. Otherwise the first rotation of the <em>next</em> fight would be
     * differenced against a yaw from ten seconds ago, putting one enormous invented turn into the
     * sample.
     */
    public synchronized void breakRotationContinuity() {
        hasLastYaw = false;
    }

    /**
     * @return {yawDeltas, signedErrors} oldest first, or null while there is not enough to judge
     */
    public synchronized double[][] takeRotations(int minimum) {
        if (rotations.size() < minimum) {
            return null;
        }
        double[] deltas = new double[rotations.size()];
        double[] errors = new double[rotations.size()];
        int i = 0;
        for (double[] entry : rotations) {
            deltas[i] = entry[0];
            errors[i] = entry[1];
            i++;
        }
        rotations.clear();
        // The next window starts from a clean yaw reference rather than differencing across a gap.
        hasLastYaw = false;
        return new double[][]{deltas, errors};
    }

    // -- swings ------------------------------------------------------------------------------

    public synchronized void recordSwing(long nowMillis) {
        push(swings, nowMillis, SWING_CAPACITY);
    }

    /** @return swing timestamps oldest first, or null while there is not enough to judge */
    public synchronized long[] takeSwings(int minimum) {
        if (swings.size() < minimum) {
            return null;
        }
        long[] out = new long[swings.size()];
        int i = 0;
        for (Long value : swings) {
            out[i++] = value;
        }
        swings.clear();
        return out;
    }

    // -- movement ----------------------------------------------------------------------------

    public synchronized void recordMovement(MovementSample sample) {
        push(movement, sample, MOVEMENT_CAPACITY);
    }

    /** @return the samples oldest first, or null while there is not enough to judge */
    public synchronized List<MovementSample> takeMovement(int minimum) {
        if (movement.size() < minimum) {
            return null;
        }
        List<MovementSample> out = new ArrayList<MovementSample>(movement);
        movement.clear();
        return out;
    }

    // -- block placements --------------------------------------------------------------------

    /**
     * @param pitch        their view pitch when the block appeared, degrees
     * @param angleToBlock angle between their view and the block, degrees
     */
    public synchronized void recordPlacement(double pitch, double angleToBlock) {
        push(placements, new double[]{pitch, angleToBlock}, PLACEMENT_CAPACITY);
    }

    /** @return {pitches, angles} oldest first, or null while there is not enough to judge */
    public synchronized double[][] takePlacements(int minimum) {
        if (placements.size() < minimum) {
            return null;
        }
        double[] pitches = new double[placements.size()];
        double[] angles = new double[placements.size()];
        int i = 0;
        for (double[] entry : placements) {
            pitches[i] = entry[0];
            angles[i] = entry[1];
            i++;
        }
        placements.clear();
        return new double[][]{pitches, angles};
    }

    // -- hits they landed on you ---------------------------------------------------------------

    /** Only hits on you can be measured honestly; see {@link HitSample}. */
    public synchronized void recordHitOnYou(HitSample hit) {
        push(hitsOnYou, hit, HIT_CAPACITY);
    }

    /**
     * Reach and backtrack both read the same hits, so this hands over a copy without clearing;
     * {@link #clearHits()} spends them once both have had their say.
     */
    public synchronized List<HitSample> peekHits(int minimum) {
        if (hitsOnYou.size() < minimum) {
            return null;
        }
        return new ArrayList<HitSample>(hitsOnYou);
    }

    public synchronized void clearHits() {
        hitsOnYou.clear();
    }

    public synchronized int hitCount() {
        return hitsOnYou.size();
    }

    // -- knockback taken -----------------------------------------------------------------------

    /** How far they moved in the ticks after taking a hit. */
    public synchronized void recordHitDisplacement(double blocks) {
        push(displacements, blocks, HIT_CAPACITY);
    }

    /** @return the displacements oldest first, or null while there is not enough to judge */
    public synchronized double[] takeHitDisplacements(int minimum) {
        if (displacements.size() < minimum) {
            return null;
        }
        double[] out = new double[displacements.size()];
        int i = 0;
        for (Double value : displacements) {
            out[i++] = value;
        }
        displacements.clear();
        return out;
    }

    // -- housekeeping --------------------------------------------------------------------------

    public synchronized long getLastSeenMillis() {
        return lastSeenMillis;
    }

    public synchronized void clear() {
        rotations.clear();
        swings.clear();
        movement.clear();
        placements.clear();
        hitsOnYou.clear();
        displacements.clear();
        hasLastYaw = false;
    }

    private static <T> void push(Deque<T> queue, T value, int capacity) {
        if (queue.size() >= capacity) {
            queue.removeFirst();
        }
        queue.addLast(value);
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
