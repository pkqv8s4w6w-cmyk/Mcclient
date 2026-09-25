package dev.vantage.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDetectorTest {

    @Test
    void recognisesTheBedwarsScoreboard() {
        assertTrue(GameDetector.isBedwars("§e§lBED WARS"));
        assertTrue(GameDetector.isBedwars("§e§lBEDWARS"));
    }

    @Test
    void survivesSpacingAndCaseDifferences() {
        assertTrue(GameDetector.isBedwars("Bed Wars"));
        assertTrue(GameDetector.isBedwars("§6§lbed wars§r"));
    }

    @Test
    void aHubIsNotAGame() {
        // The case that started this: the tab list in a hub is full of people who are not
        // opponents, and nothing else distinguishes it from a real lobby.
        assertFalse(GameDetector.isBedwars("§6§lHYPIXEL"));
        assertFalse(GameDetector.isBedwars("§e§lLOBBY"));
    }

    @Test
    void otherGamesAreNotBedwars() {
        assertFalse(GameDetector.isBedwars("§e§lSKYWARS"));
        assertFalse(GameDetector.isBedwars("§c§lMURDER MYSTERY"));
    }

    @Test
    void missingOrEmptyTitlesAreNotAGame() {
        assertFalse(GameDetector.isBedwars(null));
        assertFalse(GameDetector.isBedwars(""));
        assertFalse(GameDetector.isBedwars("§r"));
    }
}
