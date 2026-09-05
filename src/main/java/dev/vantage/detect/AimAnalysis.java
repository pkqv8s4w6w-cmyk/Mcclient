package dev.vantage.detect;

/**
 * Looks for assisted aim in how a player's view tracks a target.
 *
 * <p>Worth stating what this cannot do. Another player's rotations reach the client through entity
 * look packets, where yaw and pitch are each a single byte - steps of 360/256, about 1.4 degrees.
 * The fine-grained tests a server-side anticheat runs, such as finding the common divisor of raw
 * mouse deltas, need the unquantised floats a client never receives. Those signals are simply not
 * available here, and a check claiming otherwise would be measuring rounding noise.
 *
 * <p>What survives quantisation is coarse behaviour: a large turn completed in a single tick that
 * lands on a target, and a view that stays locked on one through movement that should have
 * disturbed it. Both are measured here.
 */
public final class AimAnalysis {

    /** The resolution other players' rotations arrive at, in degrees. */
    public static final double PACKET_ROTATION_STEP = 360.0 / 256.0;

    public static final class Result {
        private final int samples;
        private final int snaps;
        private final double lockedFraction;
        private final double confidence;

        Result(int samples, int snaps, double lockedFraction, double confidence) {
            this.samples = samples;
            this.snaps = snaps;
            this.lockedFraction = lockedFraction;
            this.confidence = confidence;
        }

        public int getSamples() {
            return samples;
        }

        /** Single-tick turns that ended aimed at a target. */
        public int getSnaps() {
            return snaps;
        }

        /** Share of the window spent aimed at a target. */
        public double getLockedFraction() {
            return lockedFraction;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0, 0.0, 0.0);

    private static final int MIN_SAMPLES = 20;

    /** Snaps needed before the pattern counts as more than a lucky flick. */
    private static final int SNAP_THRESHOLD = 3;

    /** Above this share of the window on target, a human's natural drift is missing. */
    private static final double LOCK_THRESHOLD = 0.92;

    private AimAnalysis() {
    }

    /**
     * @param yawDeltas          per-tick change in yaw, degrees
     * @param angleToTarget      angle between the view and the nearest target, degrees; use a
     *                           large value for ticks with no target in range
     * @param snapDegrees        single-tick turn size that counts as a snap
     * @param lockDegrees        how close to a target counts as aimed at it
     */
    public static Result analyse(double[] yawDeltas, double[] angleToTarget,
                                 double snapDegrees, double lockDegrees) {
        if (yawDeltas == null || angleToTarget == null
                || yawDeltas.length != angleToTarget.length
                || yawDeltas.length < MIN_SAMPLES) {
            return NOTHING;
        }

        int snaps = 0;
        int locked = 0;
        int withTarget = 0;

        for (int i = 0; i < yawDeltas.length; i++) {
            boolean onTarget = angleToTarget[i] <= lockDegrees;
            // A target is "in range" for the lock measure whenever it is within a wide cone;
            // ticks spent facing nothing should not dilute the ratio.
            if (angleToTarget[i] <= 90.0) {
                withTarget++;
                if (onTarget) {
                    locked++;
                }
            }
            if (Math.abs(yawDeltas[i]) >= snapDegrees && onTarget) {
                snaps++;
            }
        }

        if (withTarget < MIN_SAMPLES) {
            return NOTHING;
        }

        double lockedFraction = locked / (double) withTarget;

        double snapConfidence = snaps >= SNAP_THRESHOLD
                ? Math.min(1.0, (snaps - SNAP_THRESHOLD + 1) / 5.0)
                : 0.0;
        double lockConfidence = lockedFraction > LOCK_THRESHOLD
                ? Math.min(1.0, (lockedFraction - LOCK_THRESHOLD) / (1.0 - LOCK_THRESHOLD))
                : 0.0;

        return new Result(yawDeltas.length, snaps, lockedFraction,
                Math.max(snapConfidence, lockConfidence));
    }
}
