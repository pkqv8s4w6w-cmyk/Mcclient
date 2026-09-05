package dev.vantage.gui.font;

/**
 * The client's type scale.
 *
 * <p>Sizes are in GUI units, not screen pixels. Minecraft's own font is nine units tall, so body
 * text belongs near that; picking sizes that look right as pixel measurements produces text at
 * roughly double the intended size, which then overflows the layout boxes it is meant to sit in.
 *
 * <p>Atlases are built lazily on first draw, so constructing these at class-load costs nothing and
 * touches no GL context.
 */
public final class Fonts {

    public static final TTFFontRenderer TITLE = new TTFFontRenderer(11.0f, true);
    public static final TTFFontRenderer BODY = new TTFFontRenderer(9.0f, false);
    public static final TTFFontRenderer BODY_BOLD = new TTFFontRenderer(9.0f, true);
    public static final TTFFontRenderer SMALL = new TTFFontRenderer(8.0f, false);
    public static final TTFFontRenderer SMALL_BOLD = new TTFFontRenderer(8.0f, true);
    public static final TTFFontRenderer TINY = new TTFFontRenderer(6.5f, false);

    private Fonts() {
    }
}
