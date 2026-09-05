package dev.vantage.game;

import dev.vantage.gui.font.MinecraftColours;

import java.util.Locale;

/**
 * Works out whether you are actually in a game, from the scoreboard's title.
 *
 * <p>This is what decides whether the threat list has anything to show. The tab list on its own is
 * not a useful signal: in a hub it carries everyone standing around, which is how unrelated names
 * ended up in the list.
 *
 * <p>An earlier attempt filtered on whether each player had a scoreboard team. That was too
 * fragile — Hypixel assigns scoreboard teams in lobbies too, for nametag colouring, and teams are
 * not assigned until a game actually starts, so it both failed to exclude hub players and risked
 * emptying the list during the countdown, which is exactly when knowing who you are up against
 * matters most.
 *
 * <p>Pure text handling so the titles it has to recognise are pinned in tests.
 */
public final class GameDetector {

    private GameDetector() {
    }

    /**
     * @param sidebarTitle the scoreboard objective's display name, colour codes and all
     * @return true while in a Bedwars game or its pre-game lobby
     */
    public static boolean isBedwars(String sidebarTitle) {
        if (sidebarTitle == null) {
            return false;
        }
        String plain = MinecraftColours.strip(sidebarTitle)
                .toUpperCase(Locale.ROOT)
                .replace(" ", "");
        return plain.contains("BEDWARS");
    }
}
