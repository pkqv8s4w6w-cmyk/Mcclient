package dev.vantage.game;

/** The eight Bedwars team colours, keyed by the chat colour code the server uses for them. */
public enum TeamColour {

    RED('c', "Red", 0xFFFF5555),
    BLUE('9', "Blue", 0xFF5555FF),
    GREEN('a', "Green", 0xFF55FF55),
    YELLOW('e', "Yellow", 0xFFFFFF55),
    AQUA('b', "Aqua", 0xFF55FFFF),
    WHITE('f', "White", 0xFFFFFFFF),
    PINK('d', "Pink", 0xFFFF55FF),
    GREY('8', "Grey", 0xFFAAAAAA),
    UNKNOWN('7', "Unknown", 0xFFAAAAAA);

    private final char colourCode;
    private final String displayName;
    private final int argb;

    TeamColour(char colourCode, String displayName, int argb) {
        this.colourCode = colourCode;
        this.displayName = displayName;
        this.argb = argb;
    }

    public char getColourCode() {
        return colourCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getArgb() {
        return argb;
    }

    public static TeamColour fromColourCode(char code) {
        for (TeamColour colour : values()) {
            if (colour.colourCode == Character.toLowerCase(code) && colour != UNKNOWN) {
                return colour;
            }
        }
        return UNKNOWN;
    }

    public static TeamColour fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        String trimmed = name.trim();
        for (TeamColour colour : values()) {
            if (colour.displayName.equalsIgnoreCase(trimmed)) {
                return colour;
            }
        }
        // Hypixel writes "Gray"; British spelling is used internally.
        if ("gray".equalsIgnoreCase(trimmed)) {
            return GREY;
        }
        return UNKNOWN;
    }
}
