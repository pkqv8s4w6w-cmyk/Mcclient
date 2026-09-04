package dev.vantage.gui;

/** Easing curves, all taking and returning a 0..1 progress value. */
public final class Easing {

    private Easing() {
    }

    public static double clamp(double progress) {
        return progress < 0.0 ? 0.0 : (progress > 1.0 ? 1.0 : progress);
    }

    /** Fast start, long settle. The default for panels and expansions. */
    public static double outQuint(double progress) {
        double t = clamp(progress);
        double inverted = 1.0 - t;
        return 1.0 - inverted * inverted * inverted * inverted * inverted;
    }

    public static double outCubic(double progress) {
        double inverted = 1.0 - clamp(progress);
        return 1.0 - inverted * inverted * inverted;
    }

    public static double inOutCubic(double progress) {
        double t = clamp(progress);
        return t < 0.5
                ? 4.0 * t * t * t
                : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
    }

    /** Overshoots slightly before settling. Good for toggles, wrong for anything large. */
    public static double outBack(double progress) {
        double overshoot = 1.70158;
        double t = clamp(progress) - 1.0;
        return 1.0 + (overshoot + 1.0) * t * t * t + overshoot * t * t;
    }
}
