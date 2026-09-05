package dev.vantage.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Movement checks that run off per-tick position samples.
 *
 * <p>All of them ignore any pair of samples where either end was teleported, and none of them
 * judges on a single tick. A lag spike, a server-side reposition, or one dropped packet will each
 * produce a reading that looks impossible; only sustained behaviour means anything.
 */
public final class MovementAnalysis {

    /** Sprint-jumping peaks near this for a tick or two, so a sustained median above it is odd. */
    public static final double SPRINT_JUMP_PEAK = 0.58;

    /** A vanilla jump reaches about this height. */
    public static final double VANILLA_JUMP_HEIGHT = 1.2519;

    private static final int MIN_SAMPLES = 20;

    public static final class Result {
        private final double measured;
        private final double confidence;

        Result(double measured, double confidence) {
            this.measured = measured;
            this.confidence = confidence;
        }

        /** The figure the check was judging, for the message it produces. */
        public double getMeasured() {
            return measured;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0.0, 0.0);

    private MovementAnalysis() {
    }

    private static double confidenceFor(double measured, double allowed, double fullyConfidentAt) {
        if (measured <= allowed) {
            return 0.0;
        }
        return Math.min(1.0, (measured - allowed) / Math.max(1e-9, fullyConfidentAt - allowed));
    }

    /**
     * Horizontal speed, judged on the median tick rather than the fastest.
     *
     * <p>The median is the point: one 0.9 block tick is a lag correction, a median of 0.9 is not
     * something a player can produce.
     *
     * @param allowed the fastest sustainable speed to accept, in blocks per tick
     */
    public static Result speed(List<MovementSample> samples, double allowed) {
        if (samples == null || samples.size() < MIN_SAMPLES) {
            return NOTHING;
        }
        List<Double> steps = new ArrayList<Double>();
        for (int i = 1; i < samples.size(); i++) {
            MovementSample previous = samples.get(i - 1);
            MovementSample current = samples.get(i);
            if (previous.teleported || current.teleported) {
                continue;
            }
            steps.add(previous.horizontalDistanceTo(current));
        }
        if (steps.size() < MIN_SAMPLES / 2) {
            return NOTHING;
        }
        Collections.sort(steps);
        double median = steps.get(steps.size() / 2);
        return new Result(median, confidenceFor(median, allowed, allowed * 1.6));
    }

    /**
     * The longest run of airborne ticks during which they never descended.
     *
     * <p>In vanilla you begin falling within a few ticks of leaving the ground, so a long run of
     * airborne ticks with no downward movement is flight or a hover.
     *
     * <p>Cannot tell a ladder, water, a boat or a slime bounce apart from flight, which is why the
     * default run length is deliberately long.
     *
     * @param allowedTicks how many non-descending airborne ticks to tolerate
     */
    public static Result hover(List<MovementSample> samples, int allowedTicks) {
        if (samples == null || samples.size() < MIN_SAMPLES) {
            return NOTHING;
        }
        int longest = 0;
        int run = 0;
        for (int i = 1; i < samples.size(); i++) {
            MovementSample previous = samples.get(i - 1);
            MovementSample current = samples.get(i);
            if (previous.teleported || current.teleported || current.onGround) {
                run = 0;
                continue;
            }
            // A tiny negative tolerance, since positions arrive quantised to 1/32 of a block.
            if (current.y - previous.y >= -0.03) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 0;
            }
        }
        return new Result(longest, confidenceFor(longest, allowedTicks, allowedTicks * 2.0));
    }

    /**
     * The highest a player rose above the ground they left.
     *
     * <p>Kept conservative on purpose. Jump boost is real and the client cannot reliably see
     * another player's effects, so the allowance sits above what a boosted jump reaches rather than
     * flagging everyone who drank a potion.
     */
    public static Result jumpHeight(List<MovementSample> samples, double allowed) {
        if (samples == null || samples.size() < MIN_SAMPLES) {
            return NOTHING;
        }
        double best = 0.0;
        Double groundLevel = null;
        for (int i = 0; i < samples.size(); i++) {
            MovementSample sample = samples.get(i);
            if (sample.teleported) {
                groundLevel = null;
                continue;
            }
            if (sample.onGround) {
                groundLevel = sample.y;
                continue;
            }
            if (groundLevel != null) {
                best = Math.max(best, sample.y - groundLevel);
            }
        }
        return new Result(best, confidenceFor(best, allowed, allowed + 1.0));
    }

    /**
     * Sprinting while moving backwards.
     *
     * <p>Not possible in vanilla 1.8, which makes this one of the few checks with almost no
     * false-positive surface. Knockback can briefly push someone backwards with the sprint flag
     * still set, so it takes a sustained run rather than one tick.
     *
     * @param allowedTicks consecutive backwards-sprinting ticks to tolerate
     */
    public static Result omniSprint(List<MovementSample> samples, int allowedTicks) {
        if (samples == null || samples.size() < MIN_SAMPLES) {
            return NOTHING;
        }
        int longest = 0;
        int run = 0;
        for (int i = 1; i < samples.size(); i++) {
            MovementSample previous = samples.get(i - 1);
            MovementSample current = samples.get(i);
            if (previous.teleported || current.teleported) {
                run = 0;
                continue;
            }
            boolean movingBackwards = current.facingDot < -0.5;
            boolean actuallyMoving = previous.horizontalDistanceTo(current) > 0.05;
            if (current.sprinting && movingBackwards && actuallyMoving) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 0;
            }
        }
        return new Result(longest, confidenceFor(longest, allowedTicks, allowedTicks * 2.0));
    }
}
