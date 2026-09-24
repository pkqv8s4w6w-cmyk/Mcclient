package dev.vantage.combat;

import java.util.Random;

/**
 * Decides when the next click falls, for anything that clicks on the player's behalf.
 *
 * <p>A fixed interval is the one pattern a hand never produces, so each gap is drawn around the
 * midpoint of the chosen range with a bell-shaped spread, and the rate drifts slowly within the
 * range the way a real hand tires and recovers. Now and then a gap is stretched (a missed click) or
 * squeezed (a double-tap), which is what an actual click log looks like.
 *
 * <p>Minecraft-free and driven by an explicit clock, so the distribution is tested directly.
 */
public final class ClickTimer {

    /** Chance per click of a hand-like hiccup. */
    private static final double DROP_CHANCE = 0.04;
    private static final double SPIKE_CHANCE = 0.03;

    private final Random random;
    private long nextClickAt;
    private double drift;

    public ClickTimer() {
        this(new Random());
    }

    public ClickTimer(Random random) {
        this.random = random;
    }

    /** Forget the schedule, so the next {@link #shouldClick} fires immediately. */
    public void reset() {
        nextClickAt = 0L;
    }

    /**
     * @return true if a click is due at {@code now}; when it is, the following one is scheduled
     */
    public boolean shouldClick(long now, double minCps, double maxCps) {
        if (now < nextClickAt) {
            return false;
        }
        long delay = nextDelay(minCps, maxCps);
        // Schedule from the previous due time rather than from now, so a late tick does not lower
        // the rate; but never let a long stall bank up a burst of catch-up clicks.
        long base = nextClickAt == 0L || now - nextClickAt > delay ? now : nextClickAt;
        nextClickAt = base + delay;
        return true;
    }

    /** One gap between clicks, in milliseconds, for a rate somewhere between the two bounds. */
    public long nextDelay(double minCps, double maxCps) {
        double low = Math.max(0.5, Math.min(minCps, maxCps));
        double high = Math.max(low, Math.max(minCps, maxCps));

        // Slow wander of the working rate inside the range.
        drift += (random.nextDouble() - 0.5) * 0.2;
        drift = Math.max(-1.0, Math.min(1.0, drift));

        double centre = (low + high) / 2.0 + drift * (high - low) / 4.0;
        double spread = (high - low) / 4.0;
        double cps = centre + random.nextGaussian() * spread;
        cps = Math.max(low, Math.min(high, cps));

        double millis = 1000.0 / cps;
        double roll = random.nextDouble();
        if (roll < DROP_CHANCE) {
            millis *= 1.6 + random.nextDouble() * 0.6;
        } else if (roll < DROP_CHANCE + SPIKE_CHANCE) {
            millis *= 0.55 + random.nextDouble() * 0.2;
        }
        return Math.max(25L, Math.round(millis));
    }
}
