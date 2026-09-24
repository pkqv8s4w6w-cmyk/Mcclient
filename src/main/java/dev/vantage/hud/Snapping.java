package dev.vantage.hud;

/**
 * Alignment maths for dragging HUD elements.
 *
 * <p>Pure arithmetic on one axis at a time, with no Minecraft references, so the behaviour can be
 * tested directly. Callers run it once for x and once for y.
 */
public final class Snapping {

    /** The outcome of a snap attempt along one axis. */
    public static final class Result {
        public final float value;
        public final boolean snapped;

        Result(float value, boolean snapped) {
            this.value = value;
            this.snapped = snapped;
        }
    }

    private Snapping() {
    }

    /**
     * Pulls {@code candidate} to the nearest alignment within {@code threshold}.
     *
     * <p>Considered targets are the container's edges and centre, and for every other element:
     * aligning starts, aligning ends, aligning centres, and butting up against either side. The
     * closest wins, so an element near two guides does not jitter between them.
     *
     * @param candidate     the position the drag would otherwise produce
     * @param size          the dragged element's extent on this axis
     * @param containerSize the screen's extent on this axis
     * @param otherStarts   other elements' positions on this axis
     * @param otherSizes    matching extents, same length as {@code otherStarts}
     * @param margin        inset used for the "against the edge" targets
     */
    public static Result snap(float candidate, float size, float containerSize,
                              float[] otherStarts, float[] otherSizes,
                              float threshold, float margin) {
        if (otherStarts.length != otherSizes.length) {
            throw new IllegalArgumentException("otherStarts and otherSizes must be the same length");
        }

        float best = candidate;
        float bestDistance = threshold;
        boolean found = false;

        float[] containerTargets = {
                margin,                              // against the near edge
                containerSize - size - margin,       // against the far edge
                (containerSize - size) / 2.0f,       // centred
        };
        for (float target : containerTargets) {
            float distance = Math.abs(candidate - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
                found = true;
            }
        }

        for (int i = 0; i < otherStarts.length; i++) {
            float otherStart = otherStarts[i];
            float otherSize = otherSizes[i];
            float[] targets = {
                    otherStart,                                        // starts aligned
                    otherStart + otherSize - size,                     // ends aligned
                    otherStart + otherSize,                            // placed just after
                    otherStart - size,                                 // placed just before
                    otherStart + (otherSize - size) / 2.0f,            // centres aligned
            };
            for (float target : targets) {
                float distance = Math.abs(candidate - target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = target;
                    found = true;
                }
            }
        }

        // Never let a snap push an element off screen; a target can be negative when an element is
        // wider than the thing it aligned to.
        float clamped = Math.max(0.0f, Math.min(Math.max(0.0f, containerSize - size), best));
        return new Result(clamped, found && clamped == best);
    }
}
