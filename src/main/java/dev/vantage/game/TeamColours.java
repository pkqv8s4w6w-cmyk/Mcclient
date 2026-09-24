package dev.vantage.game;

/**
 * Turning the colours a server paints on a player into a team.
 *
 * <p>Minecraft-free, so each rule is tested directly. {@link TeamResolver} feeds it the colours it
 * reads off the entity.
 */
public final class TeamColours {

    /** The dye colours Bedwars plugins put on leather armour, matched to teams. */
    private static final int[][] ARMOUR_DYES = {
            {0xB02E26, TeamColour.RED.ordinal()},
            {0xFF5555, TeamColour.RED.ordinal()},
            {0x3C44AA, TeamColour.BLUE.ordinal()},
            {0x5555FF, TeamColour.BLUE.ordinal()},
            {0x5E7C16, TeamColour.GREEN.ordinal()},
            {0x80C71F, TeamColour.GREEN.ordinal()},
            {0x55FF55, TeamColour.GREEN.ordinal()},
            {0xFED83D, TeamColour.YELLOW.ordinal()},
            {0xFFFF55, TeamColour.YELLOW.ordinal()},
            {0x169C9C, TeamColour.AQUA.ordinal()},
            {0x3AB3DA, TeamColour.AQUA.ordinal()},
            {0x55FFFF, TeamColour.AQUA.ordinal()},
            {0xF9FFFE, TeamColour.WHITE.ordinal()},
            {0xFFFFFF, TeamColour.WHITE.ordinal()},
            {0xF38BAA, TeamColour.PINK.ordinal()},
            {0xC74EBD, TeamColour.PINK.ordinal()},
            {0xFF55FF, TeamColour.PINK.ordinal()},
            {0x474F52, TeamColour.GREY.ordinal()},
            {0x9D9D97, TeamColour.GREY.ordinal()},
            {0xAAAAAA, TeamColour.GREY.ordinal()},
    };

    /** Undyed leather; a player wearing it has no team colour to read. */
    public static final int UNDYED_LEATHER = 0xA06540;

    private TeamColours() {
    }

    /**
     * The team whose colour appears last in a formatted string, which is the colour a name is
     * drawn in. Team prefixes often start with a bold or reset code and end with the colour.
     */
    public static TeamColour fromFormatted(String formatted) {
        if (formatted == null) {
            return TeamColour.UNKNOWN;
        }
        TeamColour found = TeamColour.UNKNOWN;
        for (int i = 0; i + 1 < formatted.length(); i++) {
            if (formatted.charAt(i) == '§') {
                TeamColour colour = TeamColour.fromColourCode(formatted.charAt(i + 1));
                if (colour != TeamColour.UNKNOWN) {
                    found = colour;
                }
            }
        }
        return found;
    }

    /** The first team colour in a formatted string: the colour a name is shown in when it opens. */
    public static TeamColour firstFromFormatted(String formatted) {
        if (formatted == null) {
            return TeamColour.UNKNOWN;
        }
        for (int i = 0; i + 1 < formatted.length(); i++) {
            if (formatted.charAt(i) == '§') {
                TeamColour colour = TeamColour.fromColourCode(formatted.charAt(i + 1));
                if (colour != TeamColour.UNKNOWN) {
                    return colour;
                }
            }
        }
        return TeamColour.UNKNOWN;
    }

    /**
     * The team a leather dye belongs to, by nearest colour. Dyes further than a generous distance
     * from every team colour, and undyed leather, give {@link TeamColour#UNKNOWN}.
     */
    public static TeamColour fromArmourDye(int rgb) {
        int colour = rgb & 0xFFFFFF;
        if (colour == UNDYED_LEATHER) {
            return TeamColour.UNKNOWN;
        }
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int[] entry : ARMOUR_DYES) {
            double distance = distance(colour, entry[0]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry[1];
            }
        }
        // Past this, the dye is something no Bedwars plugin uses for a team.
        if (best < 0 || bestDistance > 110.0) {
            return TeamColour.UNKNOWN;
        }
        return TeamColour.values()[best];
    }

    private static double distance(int a, int b) {
        int dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF);
        int dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        // Weighted for how the eye sees colour, which is also roughly how dye colours were chosen.
        return Math.sqrt(2 * dr * dr + 4 * dg * dg + 3 * db * db) / 3.0;
    }
}
