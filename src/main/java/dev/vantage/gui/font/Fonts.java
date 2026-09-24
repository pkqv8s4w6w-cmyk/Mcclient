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
    public static final TTFFontRenderer TITLE_LARGE = new TTFFontRenderer(15.0f, true);

    /** Lucide icons at body size, and a larger cut for the menu's sidebar and headers. */
    public static final TTFFontRenderer ICONS = new TTFFontRenderer(
            "/assets/vantage/fonts/lucide.ttf", 9.0f, iconCharacters());
    public static final TTFFontRenderer ICONS_LARGE = new TTFFontRenderer(
            "/assets/vantage/fonts/lucide.ttf", 12.0f, iconCharacters());

    /** Every icon constant declared on {@link dev.vantage.gui.Icons}, read once by reflection. */
    private static char[] iconCharacters() {
        java.lang.reflect.Field[] fields = dev.vantage.gui.Icons.class.getFields();
        StringBuilder builder = new StringBuilder();
        for (java.lang.reflect.Field field : fields) {
            if (field.getType() == char.class) {
                try {
                    builder.append(field.getChar(null));
                } catch (IllegalAccessException ignored) {
                    // Public static constants; unreachable.
                }
            }
        }
        char[] result = new char[builder.length()];
        builder.getChars(0, builder.length(), result, 0);
        return result;
    }

    private Fonts() {
    }
}
