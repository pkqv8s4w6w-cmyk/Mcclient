package dev.vantage.gui.font;

/** One baked character: where it sits in the atlas, and how far the pen moves after drawing it. */
final class Glyph {

    final int atlasX;
    final int atlasY;
    final int width;
    final int height;
    final float advance;

    Glyph(int atlasX, int atlasY, int width, int height, float advance) {
        this.atlasX = atlasX;
        this.atlasY = atlasY;
        this.width = width;
        this.height = height;
        this.advance = advance;
    }
}
