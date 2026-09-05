package dev.vantage.detect;

import java.util.List;

/**
 * The one movement check a client can honestly run on somebody else.
 *
 * <p>There used to be four here: speed, flight, jump height and this. The other three are gone, and
 * the reason is worth writing down so nobody adds them back.
 *
 * <p>A client does not see another player's real position. Positions arrive quantised to a
 * thirty-second of a block, at whatever rate the server sends them, and the game then
 * <em>interpolates</em> the entity between the last two over the following ticks. Reading
 * {@code posY} on a client tick gives a smoothed, lagged guess, not a measurement. When the packets
 * come sparsely — which is normal for a player who is standing still, or far away, or whose
 * updates got batched — the interpolated height simply holds steady, and a check counting
 * "airborne ticks without descending" counts up. The {@code onGround} flag is no better: for a
 * remote player it is whatever the last packet claimed, and it goes stale the same way.
 *
 * <p>So the old flight check would report a player standing on a block as flying. That is the bug
 * behind the whole lobby being accused, and no threshold fixes it, because the input is not a
 * measurement of what it claims to measure. Speed and jump height rest on differencing those same
 * positions and are unsound for the same reason. On top of that, flight has not survived a
 * server-side anticheat in years, so nobody is running it and the check had nothing to find.
 *
 * <p>What is left does not depend on the positions being accurate — only on a flag the server
 * itself sets, contradicting a direction of travel too large to be rounding.
 */
public final class MovementAnalysis {

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

    private static final int MIN_SAMPLES = 20;

    private MovementAnalysis() {
    }

    private static double confidenceFor(double measured, double allowed, double fullyConfidentAt) {
        if (measured <= allowed) {
            return 0.0;
        }
        return Math.min(1.0, (measured - allowed) / Math.max(1e-9, fullyConfidentAt - allowed));
    }

    /**
     * Sprinting while moving backwards.
     *
     * <p>Not possible in vanilla 1.8: the game clears the sprint flag the moment you stop pressing
     * forward. So this is one of the few checks with almost no false-positive surface, and unlike
     * the ones that were removed it does not care whether the positions are precise — only which
     * way the player is travelling relative to their own facing, which survives both quantisation
     * and interpolation.
     *
     * <p>Knockback can briefly push someone backwards with the flag still set, so it takes a
     * sustained run rather than one tick.
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
