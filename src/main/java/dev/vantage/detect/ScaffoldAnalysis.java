package dev.vantage.detect;

/**
 * Tells automated bridging apart from the ordinary kind.
 *
 * <p>Placing blocks under yourself while walking backwards is not suspicious — it is how everyone
 * crosses a gap in Bedwars, and a check that flagged it would flag the whole lobby. The difference
 * is where the player is looking. Bridging by hand means aiming down at the block you are placing;
 * a scaffold keeps the view level and forward while blocks appear behind the player, because the
 * placement is not coming from the view at all.
 *
 * <p>So this judges two things per placement: how far the view was from level, and how far the
 * block was from where the view pointed.
 */
public final class ScaffoldAnalysis {

    public static final class Result {
        private final int placements;
        private final double automatedFraction;
        private final double confidence;

        Result(int placements, double automatedFraction, double confidence) {
            this.placements = placements;
            this.automatedFraction = automatedFraction;
            this.confidence = confidence;
        }

        public int getPlacements() {
            return placements;
        }

        public double getAutomatedFraction() {
            return automatedFraction;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isSuspicious() {
            return confidence > 0.0;
        }
    }

    public static final Result NOTHING = new Result(0, 0.0, 0.0);

    /** Fewer placements than this and a run of luck looks like a pattern. */
    private static final int MIN_PLACEMENTS = 8;

    /** Above this share of placements looking automated, it is worth saying something. */
    private static final double SUSPICIOUS_FRACTION = 0.75;

    private ScaffoldAnalysis() {
    }

    /**
     * @param pitches         view pitch at each placement, degrees; 0 is level, 90 is straight down
     * @param anglesToBlock   angle between the view and the placed block, degrees
     * @param levelPitch      pitch below which the view counts as level rather than aimed down
     * @param behindAngle     angle beyond which the block counts as away from where they looked
     */
    public static Result analyse(double[] pitches, double[] anglesToBlock,
                                 double levelPitch, double behindAngle) {
        if (pitches == null || anglesToBlock == null
                || pitches.length != anglesToBlock.length
                || pitches.length < MIN_PLACEMENTS) {
            return NOTHING;
        }

        int automated = 0;
        for (int i = 0; i < pitches.length; i++) {
            boolean viewLevel = Math.abs(pitches[i]) < levelPitch;
            boolean blockAwayFromView = anglesToBlock[i] > behindAngle;
            if (viewLevel && blockAwayFromView) {
                automated++;
            }
        }

        double fraction = automated / (double) pitches.length;
        if (fraction <= SUSPICIOUS_FRACTION) {
            return new Result(pitches.length, fraction, 0.0);
        }
        double confidence = Math.min(1.0,
                (fraction - SUSPICIOUS_FRACTION) / (1.0 - SUSPICIOUS_FRACTION));
        return new Result(pitches.length, fraction, confidence);
    }
}
