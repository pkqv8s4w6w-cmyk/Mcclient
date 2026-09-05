package dev.vantage.detect;

/**
 * Looks for assisted aim in how a player's view tracks a target.
 *
 * <p>Worth being blunt about what this cannot do. Another player's rotations reach the client
 * through entity look packets, where yaw and pitch are each a single byte — steps of 360/256, about
 * 1.4 degrees. The fine-grained tests a server-side anticheat runs, such as finding the common
 * divisor of raw mouse deltas, need the unquantised floats a client never receives. Those signals
 * are simply not available here, and a check claiming otherwise would be measuring rounding noise.
 *
 * <p>What survives quantisation is the <em>shape</em> of the movement, and that is what aim assist
 * gives away. Every implementation works the same way: a cone it engages inside, and a cap on how
 * fast it may turn the head. Those two settings leave three marks, none of which needs sub-degree
 * precision to see:
 *
 * <ol>
 *   <li><b>It does not overshoot.</b> A hand swinging a mouse toward a moving target passes it and
 *       comes back, over and over. A rate-limited correction converges and stops.
 *   <li><b>It turns at one speed.</b> Human turn rates vary wildly tick to tick. A capped assist
 *       spends most of its corrections pinned to the cap.
 *   <li><b>It never loses the target.</b> Once the target is inside the cone, the angle to it
 *       shrinks almost every tick. A person tracking by hand gives ground constantly.
 * </ol>
 *
 * <p>Any one of those can happen honestly — a calm player on low sensitivity, a target running in a
 * straight line, a short clean fight. So no single mark is enough: a verdict needs <b>two of the
 * three</b> to agree, which is what the confidence is built to express.
 *
 * <p>The check that used to live here counted large single-tick turns that ended pointed at
 * somebody. That fires constantly during ordinary strafing, and it is gone.
 */
public final class AimAnalysis {

    /** The resolution other players' rotations arrive at, in degrees. */
    public static final double PACKET_ROTATION_STEP = 360.0 / 256.0;

    /** The cone an assist is typically configured to engage inside. */
    public static final double DEFAULT_CONE_DEGREES = 30.0;

    public static final class Result {
        private final int samples;
        private final int engagements;
        private final double overshootRate;
        private final double turnVariation;
        private final double trackingRate;
        private final double confidence;

        Result(int samples, int engagements, double overshootRate, double turnVariation,
               double trackingRate, double confidence) {
            this.samples = samples;
            this.engagements = engagements;
            this.overshootRate = overshootRate;
            this.turnVariation = turnVariation;
            this.trackingRate = trackingRate;
            this.confidence = confidence;
        }

        public int getSamples() {
            return samples;
        }

        /** How many separate times they swung onto a target. */
        public int getEngagements() {
            return engagements;
        }

        /** Share of those swings that passed the target and came back. Low is the odd direction. */
        public double getOvershootRate() {
            return overshootRate;
        }

        /**
         * How much their turn speed varied while swinging onto a target, as a fraction of its
         * mean, or -1 when there was not enough turning to measure. Not the same as zero, which
         * would mean a perfectly constant turn rate — the two must never be confused.
         */
        public double getTurnVariation() {
            return turnVariation;
        }

        /** Share of in-cone ticks where the angle to the target shrank. */
        public double getTrackingRate() {
            return trackingRate;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0, 0.0, 0.0, 0.0, 0.0);

    /** Ticks of fighting needed before any of this means anything. Two seconds of combat. */
    private static final int MIN_SAMPLES = 40;

    // -- overshoot ---------------------------------------------------------------------------

    /** The angle a swing has to start beyond to count as acquiring a target rather than drifting. */
    private static final double ENGAGE_START = 20.0;

    /** And the angle it has to reach for the acquisition to count as finished. */
    private static final double ENGAGE_END = 4.0;

    /**
     * How long after acquiring to keep watching for the correction back.
     *
     * <p>Short on purpose. Passing a target and coming back is something that happens within a few
     * ticks of the swing, not a second later. Watching longer lets the <em>next</em> swing bleed
     * into this engagement, and since the next target is usually on the other side, it gets scored
     * as this engagement overshooting — handing a machine the one mark it never earns.
     */
    private static final int SETTLE_TICKS = 6;

    /**
     * How far past the target the view has to go for it to count as an overshoot.
     *
     * <p>Two packet steps. Rotations arrive rounded to about 1.4 degrees, so a view sitting exactly
     * on a target reports a sign either side of it at random from tick to tick. Counting those as
     * overshoots would credit a machine with the one thing it never does, and quietly turn the
     * check off.
     */
    private static final double MIN_OVERSHOOT_DEGREES = 3.0;

    private static final int MIN_ENGAGEMENTS = 6;

    /** Below this share of swings overshooting, a hand is not what is doing the aiming. */
    private static final double HUMAN_OVERSHOOT_RATE = 0.15;

    // -- turn rate ---------------------------------------------------------------------------

    /**
     * The smallest turn worth timing.
     *
     * <p>Nearly three quantisation steps. Below this the measured spread is mostly rounding, and a
     * check reading it would be judging the packet format rather than the player.
     */
    private static final double MEANINGFUL_TURN = 4.0;

    private static final int MIN_TURN_TICKS = 15;

    /** Coefficient of variation below which the turn speed stops looking like a wrist. */
    private static final double HUMAN_TURN_VARIATION = 0.35;

    // -- tracking ----------------------------------------------------------------------------

    private static final int MIN_CONE_TICKS = 25;

    /** Share of in-cone ticks that may close on the target before it stops looking manual. */
    private static final double HUMAN_TRACKING_RATE = 0.80;

    private AimAnalysis() {
    }

    /**
     * @param yawDeltas    per-tick change in yaw, degrees; index 0 is ignored
     * @param signedErrors horizontal angle from their view to the nearest target after each tick,
     *                     signed so that passing the target changes it, in -180..180. Use a large
     *                     magnitude for ticks with no target.
     * @param coneDegrees  how close to a target counts as engaged with it
     */
    public static Result analyse(double[] yawDeltas, double[] signedErrors, double coneDegrees) {
        if (yawDeltas == null || signedErrors == null
                || yawDeltas.length != signedErrors.length
                || yawDeltas.length < MIN_SAMPLES) {
            return NOTHING;
        }

        Overshoot overshoot = measureOvershoot(signedErrors);
        double turnVariation = measureTurnVariation(yawDeltas, signedErrors);
        Tracking tracking = measureTracking(signedErrors, coneDegrees);

        double byOvershoot = overshoot.engagements >= MIN_ENGAGEMENTS
                ? below(overshoot.rate(), HUMAN_OVERSHOOT_RATE)
                : 0.0;
        double byTurnRate = turnVariation >= 0.0
                ? below(turnVariation, HUMAN_TURN_VARIATION)
                : 0.0;
        double byTracking = tracking.ticks >= MIN_CONE_TICKS
                ? above(tracking.rate(), HUMAN_TRACKING_RATE)
                : 0.0;

        // Two of the three have to agree, which the second-highest confidence says exactly: it is
        // zero unless at least two marks are present, and it is as strong as the weaker of them.
        double confidence = secondHighest(byOvershoot, byTurnRate, byTracking);

        return new Result(yawDeltas.length, overshoot.engagements, overshoot.rate(),
                turnVariation, tracking.rate(), confidence);
    }

    /** How far below a human floor a measurement sits, as 0 to 1. */
    private static double below(double measured, double floor) {
        if (measured >= floor) {
            return 0.0;
        }
        return Math.min(1.0, (floor - measured) / floor);
    }

    /** How far above a human ceiling a measurement sits, as 0 to 1. */
    private static double above(double measured, double ceiling) {
        if (measured <= ceiling) {
            return 0.0;
        }
        return Math.min(1.0, (measured - ceiling) / (1.0 - ceiling));
    }

    private static double secondHighest(double first, double second, double third) {
        double high = Math.max(first, Math.max(second, third));
        double low = Math.min(first, Math.min(second, third));
        // The middle one, found without sorting three numbers.
        return first + second + third - high - low;
    }

    // -- the three measurements ---------------------------------------------------------------

    private static final class Overshoot {
        int engagements;
        int overshoots;

        double rate() {
            return engagements == 0 ? 0.0 : overshoots / (double) engagements;
        }
    }

    /**
     * Counts how often a swing onto a target passed it and had to come back.
     *
     * <p>An engagement starts when the target is well off to one side and ends once the view is on
     * it. The sign of the error says which side the target is on, so a sign change means the view
     * crossed it — which is what a hand does and a rate-limited correction does not.
     */
    private static Overshoot measureOvershoot(double[] errors) {
        Overshoot result = new Overshoot();
        int index = 0;
        while (index < errors.length) {
            if (Math.abs(errors[index]) < ENGAGE_START) {
                index++;
                continue;
            }
            double approachSign = Math.signum(errors[index]);

            // Find where the swing lands, if it lands at all.
            int landed = -1;
            for (int i = index + 1; i < errors.length; i++) {
                if (Math.abs(errors[i]) <= ENGAGE_END) {
                    landed = i;
                    break;
                }
                if (Math.signum(errors[i]) != approachSign
                        && Math.abs(errors[i]) >= MIN_OVERSHOOT_DEGREES) {
                    // Swung past without ever settling; that is an overshoot in its own right.
                    landed = i;
                    break;
                }
            }
            if (landed < 0) {
                break;
            }
            result.engagements++;

            // Did the view ever cross the target, either on the way in or while holding it?
            int watchUntil = Math.min(errors.length - 1, landed + SETTLE_TICKS);
            for (int i = index + 1; i <= watchUntil; i++) {
                if (Math.abs(errors[i]) >= ENGAGE_START) {
                    // They have swung off to a new target. Whatever happens next belongs to that
                    // engagement, not this one — counting it here would credit the following
                    // swing's starting side as this one overshooting.
                    break;
                }
                if (Math.abs(errors[i]) >= MIN_OVERSHOOT_DEGREES
                        && Math.signum(errors[i]) != approachSign) {
                    result.overshoots++;
                    break;
                }
            }
            // Resume from the moment it landed, not the end of the settle window. Skipping the
            // whole window swallowed the next engagement whenever swings came quickly, which for
            // a fast turn cap is every time — so the check saw half the evidence there was.
            index = landed + 1;
        }
        return result;
    }

    /**
     * How much their turn speed varied while swinging onto a target, as a fraction of its mean.
     *
     * <p>Only ticks where the turn was <em>cut short</em> count — where the player closed on the
     * target and still had a long way to go afterwards. Those are the ticks where a speed cap
     * binds, and so the only ticks where one would be visible. Once the target is nearly centred
     * every correction is small and takes whatever the target's own movement demands, so including
     * that phase would mix two completely different populations and report a wide spread for a
     * machine that has none.
     *
     * @return the coefficient of variation, or -1 when there was not enough turning to judge
     */
    private static double measureTurnVariation(double[] yawDeltas, double[] errors) {
        double total = 0.0;
        int count = 0;
        for (int i = 1; i < yawDeltas.length; i++) {
            if (!isRateLimited(yawDeltas, errors, i)) {
                continue;
            }
            total += Math.abs(yawDeltas[i]);
            count++;
        }
        if (count < MIN_TURN_TICKS) {
            return -1.0;
        }
        double mean = total / count;
        if (mean <= 0.0) {
            return -1.0;
        }

        double sum = 0.0;
        for (int i = 1; i < yawDeltas.length; i++) {
            if (!isRateLimited(yawDeltas, errors, i)) {
                continue;
            }
            double difference = Math.abs(yawDeltas[i]) - mean;
            sum += difference * difference;
        }
        return Math.sqrt(sum / count) / mean;
    }

    /** A meaningful turn toward a target that did not get all the way there. */
    private static boolean isRateLimited(double[] yawDeltas, double[] errors, int i) {
        return Math.abs(yawDeltas[i]) >= MEANINGFUL_TURN
                && Math.abs(errors[i]) < Math.abs(errors[i - 1])
                && Math.abs(errors[i]) > MEANINGFUL_TURN;
    }

    private static final class Tracking {
        int ticks;
        int closing;

        double rate() {
            return ticks == 0 ? 0.0 : closing / (double) ticks;
        }
    }

    /**
     * How reliably the angle to the target stops growing once the target is inside the cone.
     *
     * <p>"Stops growing" rather than "shrinks", with a packet step of slack. A view genuinely
     * pinned to a target reports an angle that wobbles by a rounding step in either direction, and
     * demanding a strict decrease would score that wobble as a coin flip — scoring the tightest
     * possible tracking the same as none at all. A person losing ground gives up far more than a
     * rounding step.
     */
    private static Tracking measureTracking(double[] errors, double coneDegrees) {
        Tracking result = new Tracking();
        for (int i = 1; i < errors.length; i++) {
            if (Math.abs(errors[i - 1]) > coneDegrees) {
                continue;
            }
            result.ticks++;
            if (Math.abs(errors[i]) <= Math.abs(errors[i - 1]) + PACKET_ROTATION_STEP) {
                result.closing++;
            }
        }
        return result;
    }
}
