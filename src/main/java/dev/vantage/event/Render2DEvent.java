package dev.vantage.event;

/** The in-game overlay has finished drawing. The GL matrix is in scaled screen space. */
public final class Render2DEvent {

    private final float partialTicks;
    private final int width;
    private final int height;

    public Render2DEvent(float partialTicks, int width, int height) {
        this.partialTicks = partialTicks;
        this.width = width;
        this.height = height;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
