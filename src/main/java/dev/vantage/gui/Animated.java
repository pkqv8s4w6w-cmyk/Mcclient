package dev.vantage.gui;

/**
 * A value that chases a target over time.
 *
 * <p>Uses exponential smoothing against the wall clock rather than a per-frame step, so the
 * animation takes the same wall time at 30fps and at 240fps. Per-frame stepping is the usual
 * reason interfaces feel sluggish on a slow machine and twitchy on a fast one.
 */
public final class Animated {

    private double value;
    private double target;
    private long lastUpdate = System.nanoTime();

    /** Seconds to close roughly 63% of the remaining distance. Smaller is snappier. */
    private double timeConstant;

    public Animated(double initial, double timeConstant) {
        this.value = initial;
        this.target = initial;
        this.timeConstant = timeConstant;
    }

    public Animated(double initial) {
        this(initial, 0.09);
    }

    public void setTimeConstant(double seconds) {
        this.timeConstant = Math.max(0.001, seconds);
    }

    public void setTarget(double target) {
        this.target = target;
    }

    public double getTarget() {
        return target;
    }

    /** Jumps straight to a value with no animation, for when a panel first opens. */
    public void snapTo(double value) {
        this.value = value;
        this.target = value;
        this.lastUpdate = System.nanoTime();
    }

    public double get() {
        long now = System.nanoTime();
        double elapsed = (now - lastUpdate) / 1_000_000_000.0;
        lastUpdate = now;

        // A tab-out or a lag spike would otherwise teleport the value; cap the step.
        if (elapsed > 0.25) {
            elapsed = 0.25;
        }
        double factor = 1.0 - Math.exp(-elapsed / timeConstant);
        value += (target - value) * factor;

        // Settle exactly, so a nearly-finished animation stops costing redraws.
        if (Math.abs(target - value) < 0.0005) {
            value = target;
        }
        return value;
    }

    /** True once the value has caught up, useful for skipping work behind a closed panel. */
    public boolean isSettled() {
        return Math.abs(target - value) < 0.0005;
    }
}
