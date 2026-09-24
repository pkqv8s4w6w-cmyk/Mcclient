package dev.vantage.event;

/** The world has finished drawing. The GL matrix is in world space, relative to the camera. */
public final class Render3DEvent {

    private final float partialTicks;

    public Render3DEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }
}
