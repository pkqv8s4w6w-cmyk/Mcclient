package dev.vantage.game;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tells the people in a tab list apart from the things the server put there.
 *
 * <p>Minigame lobbies carry NPCs, shopkeepers and decorative rows alongside the players, and they
 * all arrive through the same player-info packet. Listing them is how unrelated names ended up in
 * the threat list.
 *
 * <p>Two things separate them, and both are <b>positive evidence that nobody is behind an entry</b>
 * rather than an absence of evidence that somebody is. That distinction is the whole design: a rule
 * that hid entries on missing data would bring back the other bug, where real players disappear
 * from the list.
 *
 * <p>No Minecraft references, so the rule is pinned by tests rather than discovered in a lobby.
 */
public final class PlayerIdentity {

    /** Mojang's rules for a username: one to sixteen letters, digits or underscores. */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** The UUID version Mojang issues for real accounts. */
    private static final int MOJANG_UUID_VERSION = 4;

    private PlayerIdentity() {
    }

    /**
     * Whether a tab list entry belongs to an actual account.
     *
     * <p>The name has to look like a name, which rules out the decorative rows — those are usually
     * formatting, a rank tag or a score rather than sixteen legal characters.
     *
     * <p>The UUID has to be version 4. Mojang issues a random version 4 UUID for every account. A
     * server inventing a profile for an NPC derives it from a string with
     * {@code UUID.nameUUIDFromBytes}, which is MD5-based and stamps version 3 — the same mechanism
     * that produces offline-mode UUIDs. So any version but 4 means the entry was manufactured.
     */
    public static boolean isRealAccount(UUID uuid, String name) {
        if (uuid == null || name == null) {
            return false;
        }
        if (!VALID_NAME.matcher(name).matches()) {
            return false;
        }
        return uuid.version() == MOJANG_UUID_VERSION;
    }
}
