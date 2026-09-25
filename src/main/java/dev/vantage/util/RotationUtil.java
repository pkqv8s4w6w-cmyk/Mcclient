package dev.vantage.util;

/**
 * Angle maths for aiming. Minecraft-free, so the smoothing is tested directly.
 *
 * <p>Minecraft's yaw is degrees clockwise from south (+Z), and pitch is degrees below the horizon.
 * Both conventions are baked in here so nothing else has to remember them.
 */
public final class RotationUtil {

    private RotationUtil() {
    }

    /** Wraps an angle into [-180, 180). */
    public static float wrap(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    /** Shortest signed turn from one yaw to another, in [-180, 180). */
    public static float yawDifference(float from, float to) {
        return wrap(to - from);
    }

    /**
     * The yaw and pitch that look along a vector.
     *
     * @return {yaw, pitch}
     */
    public static float[] rotationsFor(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{wrap(yaw), clampPitch(pitch)};
    }

    public static float clampPitch(float pitch) {
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    /**
     * Total angle between two rotations, used for field-of-view checks. Yaw and pitch are treated
     * as independent axes, which is accurate enough near the horizon where fighting happens.
     */
    public static float angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
        float yaw = Math.abs(yawDifference(yawA, yawB));
        float pitch = Math.abs(pitchA - pitchB);
        return (float) Math.sqrt(yaw * yaw + pitch * pitch);
    }

    /**
     * Moves one rotation toward another by at most the given step on each axis.
     *
     * <p>Yaw goes the short way round, so turning from 170 to -170 is a 20 degree turn, not 340.
     * The result is expressed relative to {@code currentYaw} rather than wrapped, because the
     * player's yaw is unbounded and a wrapped value would make the camera spin a full turn.
     *
     * @return {yaw, pitch}
     */
    public static float[] stepTowards(float currentYaw, float currentPitch, float targetYaw, float targetPitch,
                                      float maxYawStep, float maxPitchStep) {
        float yawDelta = yawDifference(currentYaw, targetYaw);
        float pitchDelta = targetPitch - currentPitch;
        yawDelta = clamp(yawDelta, -maxYawStep, maxYawStep);
        pitchDelta = clamp(pitchDelta, -maxPitchStep, maxPitchStep);
        return new float[]{currentYaw + yawDelta, clampPitch(currentPitch + pitchDelta)};
    }

    /**
     * The smallest rotation step the mouse can produce at this sensitivity.
     *
     * <p>Vanilla turns mouse counts into degrees through {@code f = s * 0.6 + 0.2; f^3 * 8 * 0.15},
     * so every rotation a real mouse makes is a whole multiple of this. Snapping to it keeps
     * automated turns indistinguishable from mouse input in their step sizes.
     */
    public static float mouseStep(float sensitivity) {
        float f = sensitivity * 0.6f + 0.2f;
        return f * f * f * 8.0f * 0.15f;
    }

    /** Rounds a turn to a whole number of mouse steps, relative to where the turn started. */
    public static float snapToMouse(float from, float to, float sensitivity) {
        float step = mouseStep(sensitivity);
        if (step <= 0.0f) {
            return to;
        }
        float delta = to - from;
        return from + Math.round(delta / step) * step;
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
