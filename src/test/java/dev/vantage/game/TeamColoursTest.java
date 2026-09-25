package dev.vantage.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamColoursTest {

    @Test
    void readsTheColourANameIsDrawnInFromAPrefix() {
        assertEquals(TeamColour.RED, TeamColours.fromFormatted("§c§lR §c"));
        assertEquals(TeamColour.BLUE, TeamColours.fromFormatted("§9"));
        // Grey brackets are not a team colour, so the team colour inside them wins.
        assertEquals(TeamColour.GREEN, TeamColours.fromFormatted("§7[§aG§7] "));
    }

    @Test
    void noColourMeansUnknown() {
        assertEquals(TeamColour.UNKNOWN, TeamColours.fromFormatted(""));
        assertEquals(TeamColour.UNKNOWN, TeamColours.fromFormatted(null));
        assertEquals(TeamColour.UNKNOWN, TeamColours.fromFormatted("§l§r"));
    }

    @Test
    void matchesVanillaDyeColoursToTeams() {
        assertEquals(TeamColour.RED, TeamColours.fromArmourDye(0xB02E26));
        assertEquals(TeamColour.BLUE, TeamColours.fromArmourDye(0x3C44AA));
        assertEquals(TeamColour.YELLOW, TeamColours.fromArmourDye(0xFED83D));
        assertEquals(TeamColour.AQUA, TeamColours.fromArmourDye(0x169C9C));
        assertEquals(TeamColour.PINK, TeamColours.fromArmourDye(0xF38BAA));
        assertEquals(TeamColour.GREY, TeamColours.fromArmourDye(0x474F52));
    }

    @Test
    void matchesChatColourDyesThatSomePluginsUse() {
        assertEquals(TeamColour.RED, TeamColours.fromArmourDye(0xFF5555));
        assertEquals(TeamColour.GREEN, TeamColours.fromArmourDye(0x55FF55));
    }

    @Test
    void undyedLeatherHasNoTeam() {
        assertEquals(TeamColour.UNKNOWN, TeamColours.fromArmourDye(TeamColours.UNDYED_LEATHER));
    }
}
