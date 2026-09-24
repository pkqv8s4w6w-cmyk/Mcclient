package dev.vantage.game;

import dev.vantage.gui.font.MinecraftColours;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads team standings out of the Bedwars scoreboard.
 *
 * <p>Hypixel writes one line per team in the sidebar, of the form
 * {@code <colour><Letter> <TeamName>: <status>}, where the status is a tick while the bed stands,
 * a survivor count once it is broken, and a cross when the team is out. Your own team gets a
 * trailing marker.
 *
 * <p>Pure text handling with no Minecraft references, so the shapes this has to survive can be
 * tested directly rather than discovered mid-game.
 */
public final class SidebarParser {

    /** Both the light and heavy forms appear depending on where the text came from. */
    private static final String TICKS = "✓✔";
    private static final String CROSSES = "✗✘";

    private static final Pattern TEAM_LINE = Pattern.compile(
            "^\\s*(\\S)\\s+([A-Za-z]+)\\s*:\\s*(.*)$");

    private SidebarParser() {
    }

    /**
     * @param lines sidebar lines, top to bottom, with their colour codes still attached
     * @return team standings keyed by lower-case team name
     */
    public static Map<String, TeamState> parse(List<String> lines) {
        Map<String, TeamState> states = new HashMap<String, TeamState>();
        if (lines == null) {
            return states;
        }
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            TeamState state = parseLine(raw);
            if (state != null && state.getColour() != TeamColour.UNKNOWN) {
                states.put(state.getColour().getDisplayName().toLowerCase(Locale.ROOT), state);
            }
        }
        return states;
    }

    /** @return the parsed team, or null when the line is not a team line at all */
    public static TeamState parseLine(String raw) {
        String stripped = MinecraftColours.strip(raw).trim();
        Matcher matcher = TEAM_LINE.matcher(stripped);
        if (!matcher.matches()) {
            return null;
        }

        String teamName = matcher.group(2);
        String status = matcher.group(3).trim();

        TeamColour colour = TeamColour.fromName(teamName);
        if (colour == TeamColour.UNKNOWN) {
            // Fall back to the line's own colour, which is the team's, for a localised name.
            colour = TeamColour.fromColourCode(firstColourCode(raw));
        }
        if (colour == TeamColour.UNKNOWN) {
            return null;
        }

        boolean yourTeam = status.toUpperCase(Locale.ROOT).contains("YOU");
        boolean eliminated = containsAny(status, CROSSES);
        boolean bedIntact = containsAny(status, TICKS);

        int playersAlive = -1;
        if (!bedIntact && !eliminated) {
            playersAlive = firstNumber(status);
            if (playersAlive < 0) {
                // Neither a tick, a cross, nor a count: not a team line after all.
                return null;
            }
        }
        return new TeamState(colour, bedIntact, eliminated, playersAlive, yourTeam);
    }

    private static boolean containsAny(String text, String characters) {
        for (int i = 0; i < characters.length(); i++) {
            if (text.indexOf(characters.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static int firstNumber(String text) {
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return -1;
        }
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(text.substring(start, end));
        } catch (NumberFormatException impossible) {
            return -1;
        }
    }

    private static char firstColourCode(String raw) {
        for (int i = 0; i < raw.length() - 1; i++) {
            if (raw.charAt(i) == '§') {
                return raw.charAt(i + 1);
            }
        }
        return '7';
    }
}
