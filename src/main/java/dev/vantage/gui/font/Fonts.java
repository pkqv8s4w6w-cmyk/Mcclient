package dev.vantage.gui.font;

/**
 * The client's type scale.
 *
 * <p>Atlases are built lazily on first draw, so constructing these at class-load costs nothing and
 * touches no GL context.
 */
public final class Fonts {

    public static final TTFFontRenderer TITLE = new TTFFontRenderer(19.0f, true);
    public static final TTFFontRenderer BODY = new TTFFontRenderer(16.0f, false);
    public static final TTFFontRenderer BODY_BOLD = new TTFFontRenderer(16.0f, true);
    public static final TTFFontRenderer SMALL = new TTFFontRenderer(14.0f, false);
    public static final TTFFontRenderer SMALL_BOLD = new TTFFontRenderer(14.0f, true);
    public static final TTFFontRenderer TINY = new TTFFontRenderer(12.0f, false);

    private Fonts() {
    }
}
