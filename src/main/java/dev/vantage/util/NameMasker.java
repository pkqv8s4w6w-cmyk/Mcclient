package dev.vantage.util;

/**
 * Swaps one player name for another inside a line of text.
 *
 * <p>Matches whole names only. A player called "Bob" must not turn "Bobby" into "Nickby", and
 * Minecraft names are exactly the characters {@code [A-Za-z0-9_]}, so anything either side of a
 * match has to fall outside that set for it to count.
 *
 * <p>Pure text handling with no Minecraft references, so the cases that matter are tested rather
 * than discovered in chat.
 */
public final class NameMasker {

    private NameMasker() {
    }

    /**
     * @param text        the line to rewrite, colour codes and all
     * @param realName    the name to hide
     * @param replacement what to show instead
     * @return the rewritten line, or {@code text} unchanged when there was nothing to do
     */
    public static String mask(String text, String realName, String replacement) {
        if (text == null || realName == null || realName.isEmpty() || replacement == null) {
            return text;
        }
        if (realName.equals(replacement) || text.indexOf(realName.charAt(0)) < 0) {
            return text;
        }

        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int found = text.indexOf(realName, index);
            if (found < 0) {
                out.append(text, index, text.length());
                break;
            }
            int after = found + realName.length();
            boolean wholeName = !isNamePart(text, found - 1) && !isNamePart(text, after);

            out.append(text, index, found);
            out.append(wholeName ? replacement : realName);
            index = after;
        }
        return out.toString();
    }

    /**
     * Whether the character at {@code index} belongs to a player name.
     *
     * <p>Colour codes are the trap here: a name arrives from the server as "§fNotch§7", and the
     * "f" of the code is a letter, so a plain character test reads it as part of a name and
     * refuses to match. The character after a section sign is formatting, never a name.
     */
    private static boolean isNamePart(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        char character = text.charAt(index);
        if (!Character.isLetterOrDigit(character) && character != '_') {
            return false;
        }
        return index == 0 || text.charAt(index - 1) != '§';
    }
}
