package dev.vantage.detect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Players the detector currently considers suspicious, so other parts of the interface - the
 * threat list in particular - can mark them without being coupled to the detector itself.
 */
public final class Flagged {

    private static final Map<String, String> reasons =
            Collections.synchronizedMap(new LinkedHashMap<String, String>());

    private Flagged() {
    }

    public static void set(String playerName, String reason) {
        reasons.put(playerName, reason);
    }

    public static void unset(String playerName) {
        reasons.remove(playerName);
    }

    public static boolean is(String playerName) {
        return reasons.containsKey(playerName);
    }

    /** @return why this player is flagged, or null when they are not */
    public static String reason(String playerName) {
        return reasons.get(playerName);
    }

    public static void clear() {
        reasons.clear();
    }

    public static int count() {
        return reasons.size();
    }
}
