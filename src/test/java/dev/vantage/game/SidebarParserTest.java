package dev.vantage.game;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarParserTest {

    @Test
    void readsATeamWithItsBedStanding() {
        TeamState state = SidebarParser.parseLine("§aG §fGreen: §a✓");
        assertNotNull(state);
        assertEquals(TeamColour.GREEN, state.getColour());
        assertTrue(state.isBedIntact());
        assertFalse(state.isEliminated());
    }

    @Test
    void readsTheHeavyTickHypixelActuallySends() {
        TeamState state = SidebarParser.parseLine("§cR §fRed: §a✔");
        assertNotNull(state);
        assertTrue(state.isBedIntact(), "the heavy check mark must count as a bed");
    }

    @Test
    void aBrokenBedShowsSurvivorsInstead() {
        TeamState state = SidebarParser.parseLine("§eY §fYellow: §a3");
        assertNotNull(state);
        assertFalse(state.isBedIntact());
        assertFalse(state.isEliminated());
        assertEquals(3, state.getPlayersAlive());
    }

    @Test
    void anEliminatedTeamIsMarkedOut() {
        TeamState state = SidebarParser.parseLine("§9B §fBlue: §c✘");
        assertNotNull(state);
        assertTrue(state.isEliminated());
        assertFalse(state.isBedIntact());
    }

    @Test
    void yourOwnTeamIsRecognised() {
        TeamState state = SidebarParser.parseLine("§bA §fAqua: §a✓ §7YOU");
        assertNotNull(state);
        assertTrue(state.isYourTeam());
        assertTrue(state.isBedIntact());
    }

    @Test
    void nonTeamLinesAreIgnored() {
        assertNull(SidebarParser.parseLine("§eBed Wars"));
        assertNull(SidebarParser.parseLine("§7www.hypixel.net"));
        assertNull(SidebarParser.parseLine(""));
        assertNull(SidebarParser.parseLine("§fDiamond: §b2"),
                "a generator line has no team colour and must not be taken for a team");
    }

    @Test
    void aWholeSidebarParsesIntoTeams() {
        Map<String, TeamState> teams = SidebarParser.parse(Arrays.asList(
                "§6§lBED WARS",
                "§7 09/04/26",
                "§fR §fRed: §a✔",
                "§fB §fBlue: §a2",
                "§fG §fGreen: §c✘",
                "§fY §fYellow: §a✔ §7YOU",
                "§fBeds: §a3"));

        assertEquals(4, teams.size(), "four team lines, and nothing else should be counted");
        assertTrue(teams.get("red").isBedIntact());
        assertEquals(2, teams.get("blue").getPlayersAlive());
        assertTrue(teams.get("green").isEliminated());
        assertTrue(teams.get("yellow").isYourTeam());
    }

    @Test
    void greyIsAcceptedInEitherSpelling() {
        assertEquals(TeamColour.GREY, TeamColour.fromName("Gray"));
        assertEquals(TeamColour.GREY, TeamColour.fromName("Grey"));
    }

    @Test
    void colourCodesMapToTeams() {
        assertEquals(TeamColour.RED, TeamColour.fromColourCode('c'));
        assertEquals(TeamColour.GREEN, TeamColour.fromColourCode('a'));
        assertEquals(TeamColour.UNKNOWN, TeamColour.fromColourCode('0'));
    }

    @Test
    void aNullSidebarYieldsNothingRatherThanThrowing() {
        assertTrue(SidebarParser.parse(null).isEmpty());
    }
}
