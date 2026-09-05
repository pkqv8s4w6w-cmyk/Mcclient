package dev.vantage.game;

import dev.vantage.gui.font.MinecraftColours;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Works out who killed whom from a chat line.
 *
 * <p>Hypixel has dozens of death messages and adds more, so matching their wording is a losing
 * game. Instead this looks for names it already knows from the tab list: the first is the victim,
 * the second, if any, is the killer. That survives new death messages, rank prefixes and the
 * server's colour codes without needing a pattern for each phrasing.
 *
 * <p>Pure text handling, so the false-positive cases can be pinned down in tests.
 */
public final class DeathMessageParser {

    /** One parsed death. {@code killer} is null for a death nobody gets credit for. */
    public static final class Kill {
        private final String victim;
        private final String killer;
        private final boolean finalKill;

        Kill(String victim, String killer, boolean finalKill) {
            this.victim = victim;
            this.killer = killer;
            this.finalKill = finalKill;
        }

        public String getVictim() {
            return victim;
        }

        public String getKiller() {
            return killer;
        }

        public boolean hasKiller() {
            return killer != null;
        }

        public boolean isFinalKill() {
            return finalKill;
        }
    }

    private DeathMessageParser() {
    }

    /**
     * @param raw        the chat line, colour codes and all
     * @param knownNames names currently in the lobby, from the tab list
     * @return the parsed kill, or null when this is not a death message
     */
    public static Kill parse(String raw, Set<String> knownNames) {
        if (raw == null || knownNames == null || knownNames.isEmpty()) {
            return null;
        }
        String message = MinecraftColours.strip(raw).trim();
        if (message.isEmpty()) {
            return null;
        }

        // Player chat carries a colon between the sender and their text. Without this guard,
        // "Notch: nice one Player2" reads as Player2 killing Notch.
        if (message.indexOf(':') >= 0) {
            return null;
        }

        List<String> mentioned = namesInOrder(message, knownNames);
        if (mentioned.isEmpty()) {
            return null;
        }

        String victim = mentioned.get(0);
        String killer = null;
        for (int i = 1; i < mentioned.size(); i++) {
            if (!mentioned.get(i).equals(victim)) {
                killer = mentioned.get(i);
                break;
            }
        }

        boolean finalKill = message.toUpperCase(java.util.Locale.ROOT).contains("FINAL KILL");
        return new Kill(victim, killer, finalKill);
    }

    private static List<String> namesInOrder(String message, Set<String> knownNames) {
        List<String> found = new ArrayList<String>(2);
        for (String token : message.split("\\s+")) {
            String cleaned = trimPunctuation(token);
            if (!cleaned.isEmpty() && knownNames.contains(cleaned)) {
                found.add(cleaned);
            }
        }
        return found;
    }

    /** Strips the trailing full stop and the brackets around rank tags. */
    private static String trimPunctuation(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && !isNameCharacter(token.charAt(start))) {
            start++;
        }
        while (end > start && !isNameCharacter(token.charAt(end - 1))) {
            end--;
        }
        return token.substring(start, end);
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
