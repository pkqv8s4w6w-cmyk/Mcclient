package dev.vantage.gui.font;

/**
 * The sixteen legacy chat colours.
 *
 * <p>Needed because server text - player names, scoreboard lines, Bedwars team prefixes - arrives
 * with section-sign codes embedded, and the threat list is far easier to read when a green team's
 * name is actually green.
 */
public final class MinecraftColours {

    private static final int[][] PALETTE = {
            {0, 0, 0},         // 0 black
            {0, 0, 170},       // 1 dark blue
            {0, 170, 0},       // 2 dark green
            {0, 170, 170},     // 3 dark aqua
            {170, 0, 0},       // 4 dark red
            {170, 0, 170},     // 5 dark purple
            {255, 170, 0},     // 6 gold
            {170, 170, 170},   // 7 gray
            {85, 85, 85},      // 8 dark gray
            {85, 85, 255},     // 9 blue
            {85, 255, 85},     // a green
            {85, 255, 255},    // b aqua
            {255, 85, 85},     // c red
            {255, 85, 255},    // d light purple
            {255, 255, 85},    // e yellow
            {255, 255, 255},   // f white
    };

    private MinecraftColours() {
    }

    /** @return rgb triplet for a colour code, or null if the character is not one */
    public static int[] resolve(char code) {
        int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        return index < 0 ? null : PALETTE[index];
    }

    /** Packs a colour code straight to opaque ARGB, or returns {@code fallback} if unrecognised. */
    public static int toArgb(char code, int fallback) {
        int[] rgb = resolve(code);
        if (rgb == null) {
            return fallback;
        }
        return 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    public static String strip(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
